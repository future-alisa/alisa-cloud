package com.alisa.security;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TenantSchemaVerifierTest {
    @Test void migratedSchemasCannotBeServedInLegacyMode() throws Exception {
        var datasource=mock(javax.sql.DataSource.class);var connection=mock(java.sql.Connection.class);var metadata=mock(java.sql.DatabaseMetaData.class);var columns=mock(java.sql.ResultSet.class);
        when(datasource.getConnection()).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getColumns(null,null,"tb_target",null)).thenReturn(columns);when(columns.next()).thenReturn(true,false);when(columns.getString("COLUMN_NAME")).thenReturn("tenant_id");
        var props=new ResourceSecurityProperties();props.setTenantTable("tb_target");
        assertThrows(IllegalStateException.class,()->new TenantSchemaVerifier(datasource,props).afterPropertiesSet());
    }
    @Test void tenantModeCannotStartBeforeTheSchemaIsMigrated() throws Exception {
        var datasource=mock(javax.sql.DataSource.class);var connection=mock(java.sql.Connection.class);var metadata=mock(java.sql.DatabaseMetaData.class);var columns=mock(java.sql.ResultSet.class);
        when(datasource.getConnection()).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);when(metadata.getColumns(null,null,"tb_todo",null)).thenReturn(columns);
        var props=new ResourceSecurityProperties();props.setTenantTable("tb_todo");props.setRequireTenant(true);
        assertThrows(IllegalStateException.class,()->new TenantSchemaVerifier(datasource,props).afterPropertiesSet());
    }
}
