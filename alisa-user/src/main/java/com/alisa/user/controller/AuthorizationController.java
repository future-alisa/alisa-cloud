package com.alisa.user.controller;

import java.util.List;
import java.util.Map;
import com.alisa.authorization.AuthorizationEngine;
import com.alisa.authorization.config.AuthorizationProperties;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.PolicyAdministration;
import com.alisa.user.service.AccessTokenService;
import com.alisa.util.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/authorization")
@ConditionalOnProperty(prefix="alisa.authorization", name="enabled", havingValue="true")
public class AuthorizationController {
    private com.alisa.user.tenant.TenantDirectory tenants;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setTenants(com.alisa.user.tenant.TenantDirectory value) {tenants=value;}
    public record CheckRequest(String applicationId, String resourceType, String resourceId, String action) {}
    public record RoleInput(String description) {}
    public record GrantInput(String resourceType, String resourceId, String action) {}
    public record BindingInput(String subjectType, String subjectId) {}
    private final AuthorizationEngine engine;
    private final AuthorizationProperties properties;
    private final AccessTokenService tokens;

    public AuthorizationController(AuthorizationEngine engine, AuthorizationProperties properties, AccessTokenService tokens) {
        this.engine = engine;
        this.properties = properties;
        this.tokens = tokens;
    }

    @PostMapping("/check")
    public ResponseEntity<Result<AuthorizationDecision>> check(
            @RequestHeader(value="Authorization",required=false) String token, @RequestBody CheckRequest request) {
        var identity=tokens.identity(token);
        tokens.requireScope(token,request.applicationId(),request.resourceType(),request.action());
        Map<String,String> context=Map.of();
        if(!identity.tenantId().isBlank()) {
            if(tenants==null || !tenants.active(identity.tenantId()) || "local".equals(properties.getApplications().get(request.applicationId())))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"租户授权未配置");
            if(!identity.application()) tenants.member(identity.tenantId(),identity.userId());
            if(!identity.delegated()) tenants.validateTokenVersion(identity.tenantId(),identity.userId(),identity.tenantVersion());
            context=Map.of("tenantId",identity.tenantId());
        }
        AuthorizationDecision decision = engine.evaluate(new AuthorizationRequest(request.applicationId(),
                new AuthorizationSubject(identity.application()?"APPLICATION":"USER",identity.application()?identity.clientId():identity.userId()),
                new AuthorizationResource(request.resourceType(),request.resourceId()),request.action(),context));
        if(tenants!=null && !identity.tenantId().isBlank()) {
            try {
                String details=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("applicationId",request.applicationId(),"resourceType",request.resourceType(),"action",request.action(),"subjectType",identity.application()?"APPLICATION":"USER"));
                String object=request.resourceId();
                tenants.audit(identity.tenantId(),identity.application()?identity.clientId():identity.userId(),"ACCESS_"+decision.effect(),object.substring(0,Math.min(object.length(),100)),details);
            } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException(e);}
        }
        Result<AuthorizationDecision> result = new Result<>(decision);
        if (decision.effect() == AuthorizationDecision.Effect.INDETERMINATE) {
            result.setCode(503);
            result.setMessage("授权提供者暂不可用");
            return ResponseEntity.status(503).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/applications/{app}/roles")
    public Result<List<PolicyRole>> roles(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app) {
        return new Result<>(administration(token, app).roles(app));
    }

    @PutMapping("/applications/{app}/roles/{role}")
    public Result<Boolean> saveRole(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role, @RequestBody RoleInput request) {
        administration(token, app).saveRole(new PolicyRole(app, role, request.description()));
        return new Result<>(true);
    }

    @DeleteMapping("/applications/{app}/roles/{role}")
    public Result<Boolean> deleteRole(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role) {
        administration(token, app).deleteRole(app, role);
        return new Result<>(true);
    }

    @GetMapping("/applications/{app}/roles/{role}/grants")
    public Result<List<PermissionGrant>> grants(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role) {
        return new Result<>(administration(token, app).grants(app, role));
    }

    @PutMapping("/applications/{app}/roles/{role}/grants")
    public Result<Boolean> grant(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role, @RequestBody GrantInput request) {
        administration(token, app).grant(new PermissionGrant(app, role, request.resourceType(), request.resourceId(), request.action()));
        return new Result<>(true);
    }

    @DeleteMapping("/applications/{app}/roles/{role}/grants")
    public Result<Boolean> revoke(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role, @RequestBody GrantInput request) {
        administration(token, app).revoke(new PermissionGrant(app, role, request.resourceType(), request.resourceId(), request.action()));
        return new Result<>(true);
    }

    @GetMapping("/applications/{app}/roles/{role}/bindings")
    public Result<List<RoleBinding>> bindings(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role) {
        return new Result<>(administration(token, app).bindings(app, role));
    }

    @PutMapping("/applications/{app}/roles/{role}/bindings")
    public Result<Boolean> bind(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role, @RequestBody BindingInput request) {
        administration(token, app).bind(new RoleBinding(app, role, new AuthorizationSubject(request.subjectType(), request.subjectId())));
        return new Result<>(true);
    }

    @DeleteMapping("/applications/{app}/roles/{role}/bindings")
    public Result<Boolean> unbind(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String app, @PathVariable String role, @RequestBody BindingInput request) {
        administration(token, app).unbind(new RoleBinding(app, role, new AuthorizationSubject(request.subjectType(), request.subjectId())));
        return new Result<>(true);
    }

    private PolicyAdministration administration(String token, String app) {
        String userId = tokens.requireFirstPartyUserId(token);
        if (!properties.getAdministrators().contains(userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要权限管理身份");
        try {
            return engine.administration(app);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "当前提供者不支持权限管理");
        }
    }
}
