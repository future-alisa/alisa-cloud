package com.alisa.security;

import com.alisa.util.JwtUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName="org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnProperty(prefix="alisa.security", name="enabled", havingValue="true")
@EnableConfigurationProperties(ResourceSecurityProperties.class)
public class ResourceSecurityAutoConfiguration {
    @Bean @ConditionalOnBean(javax.sql.DataSource.class)
    @ConditionalOnProperty(prefix="alisa.security",name="tenant-table")
    public TenantSchemaVerifier tenantSchemaVerifier(javax.sql.DataSource datasource,ResourceSecurityProperties properties) {
        return new TenantSchemaVerifier(datasource,properties);
    }
    @Bean @ConditionalOnMissingBean
    public TokenVerifier tokenVerifier(ResourceSecurityProperties properties, JwtUtil initializedJwt) {
        return new TokenVerifier(properties);
    }
    @Bean @ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public ResourceAccess resourceAccess(TokenVerifier verifier, ResourceSecurityProperties properties) {
        return new ResourceAccess(verifier, properties.getApplicationId(),properties.isRequireTenant());
    }
}
