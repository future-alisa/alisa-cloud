package com.alisa.security;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
/** Prevents accidentally serving migrated tenant data through legacy, unscoped queries. */
public class TenantSchemaVerifier implements InitializingBean {
    private final DataSource datasource;private final ResourceSecurityProperties properties;
    public TenantSchemaVerifier(DataSource datasource,ResourceSecurityProperties properties) {this.datasource=datasource;this.properties=properties;}
    public void afterPropertiesSet() throws Exception {
        String table=properties.getTenantTable();
        if(table==null || !table.matches("[a-zA-Z_][a-zA-Z0-9_]*")) throw new IllegalArgumentException("Invalid tenant table");
        boolean migrated=false;
        try(var connection=datasource.getConnection();var columns=connection.getMetaData().getColumns(connection.getCatalog(),null,table,null)) {
            while(columns.next()) if("tenant_id".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) migrated=true;
        }
        if(migrated!=properties.isRequireTenant()) throw new IllegalStateException("Tenant schema and alisa.security.require-tenant must match; refuse unscoped access to migrated data");
    }
}
