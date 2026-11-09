package com.alisa.user.tools;
import org.junit.jupiter.api.Test;
import java.sql.*;import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;
class AuthSchemaMigrationTest {
 @Test void backupFailurePreventsDdlAndSuccessfulMigrationPreservesLegacyRows()throws Exception {
  var data=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  new ResourceDatabasePopulator(new ClassPathResource("sql/oauth.sql"),new ClassPathResource("db/authorization-schema.sql")).execute(data);
  try(var c=data.getConnection()) {
   try(var s=c.createStatement()){s.execute("INSERT INTO alisa_oauth_application(client_id,owner_id,application_id,kind) VALUES('legacy','owner','demo','application')");}
   int count=AuthSchemaMigration.plan(c).size();assertTrue(count>0);
   assertThrows(IllegalStateException.class,()->AuthSchemaMigration.apply(c,()->{throw new IllegalStateException("Backup failed");}));
   assertEquals(count,AuthSchemaMigration.plan(c).size());
   AuthSchemaMigration.apply(c,()->{});assertTrue(AuthSchemaMigration.plan(c).isEmpty());
   try(var s=c.createStatement();var r=s.executeQuery("SELECT owner_id,tenant_id FROM alisa_oauth_application WHERE client_id='legacy'")){assertTrue(r.next());assertEquals("owner",r.getString(1));assertEquals("",r.getString(2));}
   AuthSchemaMigration.apply(c,()->fail("Current schema must not rerun migrations"));
  }
 }
}
