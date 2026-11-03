package com.alisa.user.tenant;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.AuthorizationProvider;
import com.alisa.user.mapper.UserMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration(proxyBeanMethods=false)
@org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
@ConditionalOnProperty(prefix="alisa.tenancy",name="enabled",havingValue="true")
public class TenantConfiguration {
    @Bean TenantDirectory tenantDirectory(JdbcTemplate jdbc,UserMapper users) {return new TenantDirectory(jdbc,users);}
    @Bean AuthorizationProvider tenantAuthorizationProvider(TenantDirectory tenants) {
        return new AuthorizationProvider() {
            public String id(){return "tenant";}
            public AuthorizationDecision decide(AuthorizationRequest request) {
                return tenants.allowed(request.context().get("tenantId"),request.applicationId(),request.subject().type(),request.subject().id(),request.resource().type(),request.resource().id(),request.action())
                    ?AuthorizationDecision.permit():AuthorizationDecision.deny("TENANT_PERMISSION_REQUIRED");
            }
        };
    }
}
