package com.alisa.user.tenant;
import com.alisa.user.service.AccessTokenService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
@RestController
public class TenantCapabilityController {
    private final ObjectProvider<TenantDirectory> tenants;private final AccessTokenService tokens;
    public TenantCapabilityController(ObjectProvider<TenantDirectory> tenants,AccessTokenService tokens) {this.tenants=tenants;this.tokens=tokens;}
    @GetMapping("/api/v1/user/tenancy") public java.util.Map<String,Boolean> capability(@RequestHeader(value="Authorization",required=false) String token) {
        tokens.requireFirstPartyUserId(token);return java.util.Map.of("enabled",tenants.getIfAvailable()!=null);
    }
}
