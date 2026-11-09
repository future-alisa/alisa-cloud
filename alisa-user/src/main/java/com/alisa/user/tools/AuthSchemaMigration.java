package com.alisa.user.tools;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPOutputStream;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Explicit operations tool. Never invoked from application startup. */
public final class AuthSchemaMigration {
    private static final String[] SCHEMAS={"/sql/oauth.sql","/db/authorization-schema.sql","/sql/tenant.sql"};
    private static final Pattern TABLE=Pattern.compile("(?is)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)");
    private static final Pattern COLUMN=Pattern.compile("(?is)^ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+(\\w+)");
    private static final Pattern INDEX=Pattern.compile("(?is)^CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)");
    private AuthSchemaMigration() {}
    public static void main(String[] args) throws Exception {
        String mode=args.length==1?args[0]:"inspect";
        if(!Set.of("inspect","apply").contains(mode)) throw new IllegalArgumentException("Mode must be inspect or apply");
        Class.forName("com.mysql.cj.jdbc.Driver");
        try(var connection=DriverManager.getConnection(required("MYSQL_URL"),required("MYSQL_USERNAME"),required("MYSQL_PASSWORD"))) {
            var pending=plan(connection);
            System.out.println("Pending auth schema statements: "+pending.size());
            if(mode.equals("inspect") || pending.isEmpty()) {System.out.println("No database changes made");return;}
            byte[] key=Base64.getDecoder().decode(required("AUTH_BACKUP_KEY"));
            if(key.length!=32) throw new IllegalArgumentException("A 256-bit backup key is required");
            String lock="alisa-admin-auth-schema";
            try(var statement=connection.prepareStatement("SELECT GET_LOCK(?,30)")) {
                statement.setString(1,lock);
                try(var result=statement.executeQuery()) {if(!result.next() || result.getInt(1)!=1)throw new IllegalStateException("Could not acquire schema migration lock");}
            }
            try {
                apply(connection,()->backup(connection,Path.of(required("AUTH_BACKUP_DIRECTORY")),key));
                if(!plan(connection).isEmpty())throw new IllegalStateException("Schema verification failed");
                System.out.println("Auth schema verified; existing rows retained");
            } finally {try(var statement=connection.prepareStatement("SELECT RELEASE_LOCK(?)")){statement.setString(1,lock);statement.execute();}}
        }
    }
    private static String required(String key) {String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+key);return value;}
    private static Map<String,String> tables(Connection connection)throws SQLException {
        Map<String,String> names=new LinkedHashMap<>();
        try(var result=connection.getMetaData().getTables(connection.getCatalog(),null,"%",new String[]{"TABLE"})) {
            while(result.next())names.put(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT),result.getString("TABLE_NAME"));
        }
        return names;
    }
    static List<String> plan(Connection connection)throws Exception {
        Map<String,String> tables=tables(connection);List<String> pending=new ArrayList<>();
        // Include newly planned tables when checking later columns and indexes.
        Set<String> newTables=new HashSet<>();
        for(String resource:SCHEMAS) {
            try(var stream=AuthSchemaMigration.class.getResourceAsStream(resource)) {
                if(stream==null)throw new IllegalStateException("Missing schema resource");
                String content=new String(stream.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)^\\s*--.*$","");
                for(String raw:content.split(";")) {
                    String sql=raw.trim();if(sql.isEmpty())continue;
                    Matcher table=TABLE.matcher(sql),column=COLUMN.matcher(sql),index=INDEX.matcher(sql);
                    boolean needed;
                    if(table.find()) {String name=table.group(1).toLowerCase(Locale.ROOT);needed=!tables.containsKey(name);if(needed)newTables.add(name);}
                    else if(column.find()) {
                        String name=column.group(1).toLowerCase(Locale.ROOT);needed=newTables.contains(name);
                        if(!needed) {
                            String actual=tables.get(name);if(actual==null)throw new IllegalStateException("Missing parent table: "+name);
                            needed=true;try(var result=connection.getMetaData().getColumns(connection.getCatalog(),null,actual,"%")) {
                                while(result.next())if(result.getString("COLUMN_NAME").equalsIgnoreCase(column.group(2)))needed=false;
                            }
                        }
                    } else if(index.find()) {
                        String name=index.group(2).toLowerCase(Locale.ROOT);needed=newTables.contains(name);
                        if(!needed) {String actual=tables.get(name);if(actual==null)throw new IllegalStateException("Missing index table");needed=true;
                            try(var result=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,actual,false,false)){while(result.next())if(index.group(1).equalsIgnoreCase(result.getString("INDEX_NAME")))needed=false;}
                        }
                    } else throw new IllegalStateException("Only checked CREATE TABLE/INDEX and ADD COLUMN migrations are allowed");
                    if(needed)pending.add(sql);
                }
            }
        }
        return pending;
    }
    @FunctionalInterface interface Backup {void run()throws Exception;}
    static void apply(Connection connection,Backup backup)throws Exception {
        List<String> pending=plan(connection);if(pending.isEmpty())return;
        backup.run(); // A completed encrypted backup is mandatory before the first DDL.
        int count=0;for(String sql:pending){try(var statement=connection.createStatement()){statement.execute(sql);}count++;}
        System.out.println("Applied auth schema statements: "+count);
    }
    private static String quoted(String name){return "`"+name.replace("`","``")+"`";}
    private static void text(DataOutputStream output,String text)throws IOException {byte[] value=text.getBytes(StandardCharsets.UTF_8);output.writeInt(value.length);output.write(value);}
    private static void backup(Connection connection,Path directory,byte[] key)throws Exception {
        Files.createDirectories(directory);Path partial=Files.createTempFile(directory,"auth-before-",".partial");
        Path completed=directory.resolve(partial.getFileName().toString().replace(".partial",".backup.aes"));
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setReadOnly(true);connection.setAutoCommit(false);
            try(var statement=connection.createStatement()){statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");}
            try(var file=new FileOutputStream(partial.toFile())) {
                file.write("ALISA-AUTH-BACKUP-1\n".getBytes(StandardCharsets.US_ASCII));file.write(nonce);
                try(var output=new DataOutputStream(new GZIPOutputStream(new CipherOutputStream(file,cipher)))) {
                    text(output,connection.getCatalog());Map<String,String> names=tables(connection);output.writeInt(names.size());
                    for(String table:names.values()) {
                        text(output,table);
                        try(var statement=connection.createStatement();var result=statement.executeQuery("SHOW CREATE TABLE "+quoted(table))) {result.next();text(output,result.getString(2));}
                        try(var statement=connection.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
                            statement.setFetchSize(Integer.MIN_VALUE);
                            try(var rows=statement.executeQuery("SELECT * FROM "+quoted(table))) {
                                var metadata=rows.getMetaData();int columns=metadata.getColumnCount();output.writeInt(columns);
                                for(int i=1;i<=columns;i++){text(output,metadata.getColumnName(i));output.writeInt(metadata.getColumnType(i));}
                                while(rows.next()){output.writeBoolean(true);for(int i=1;i<=columns;i++){byte[] value=rows.getBytes(i);output.writeInt(value==null?-1:value.length);if(value!=null)output.write(value);}}
                                output.writeBoolean(false);
                            }
                        }
                    }
                }
            }
            connection.commit();connection.setAutoCommit(true);connection.setReadOnly(false);
            Files.move(partial,completed,StandardCopyOption.ATOMIC_MOVE);
            System.out.println("Encrypted pre-migration backup completed: "+completed.getFileName());
        } catch(Exception failure) {try{connection.rollback();connection.setAutoCommit(true);connection.setReadOnly(false);}catch(SQLException ignored){}throw failure;}
        finally{Files.deleteIfExists(partial);}
    }
}
