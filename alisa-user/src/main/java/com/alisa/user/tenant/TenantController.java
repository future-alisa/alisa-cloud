package com.alisa.user.tenant;

import java.util.*;
import com.alisa.user.oauth.*;
import com.alisa.authorization.config.AuthorizationProperties;
import org.springframework.web.server.ResponseStatusException;
import com.alisa.user.service.AccessTokenService;
import com.alisa.user.mapper.UserMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController @RequestMapping("/api/v1/user/tenants")
@ConditionalOnProperty(prefix="alisa.tenancy",name="enabled",havingValue="true")
public class TenantController {
    private final TenantDirectory tenants;private final AccessTokenService tokens;private final OAuthApplications apps;private final OAuthProperties catalog;private final UserMapper users;
    private final AuthorizationProperties authorization;

    public TenantController(TenantDirectory tenants, AccessTokenService tokens, OAuthApplications apps,
                            OAuthProperties catalog, UserMapper users, AuthorizationProperties authorization) {
        this.tenants = tenants;
        this.tokens = tokens;
        this.apps = apps;
        this.catalog = catalog;
        this.users = users;
        this.authorization = authorization;
    }

    public record PlatformTenants(boolean allowed, List<TenantDirectory.PlatformTenant> tenants) {}
    public record TenantStatus(Boolean enabled) {}

    @GetMapping("/platform")
    public PlatformTenants platformTenants(@RequestHeader(value = "Authorization", required = false) String token) {
        boolean allowed = authorization.getAdministrators().contains(user(token));
        return new PlatformTenants(allowed, allowed ? tenants.platformTenants() : List.of());
    }

    @PutMapping("/platform/{tenant}/status")
    public void platformStatus(@RequestHeader(value = "Authorization", required = false) String token,
                               @PathVariable String tenant, @RequestBody TenantStatus status) {
        String actor = platformAdministrator(token);
        if (status.enabled() == null) throw new IllegalArgumentException("请选择租户状态");
        tenants.platformStatus(tenant, actor, status.enabled());
    }

    @DeleteMapping("/platform/{tenant}")
    public void deleteTenant(@RequestHeader(value = "Authorization", required = false) String token,
                             @PathVariable String tenant, @RequestParam String name) {
        tenants.deleteTenant(tenant, platformAdministrator(token), name);
    }

    private String platformAdministrator(String token) {
        String actor = user(token);
        if (!authorization.getAdministrators().contains(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return actor;
    }
    public record TenantInput(String name,boolean enabled) {}
    public record MemberInput(String userId,String role) {}
    public record AcceptInput(String invitationId,String token) {}
    public record ClientInput(String applicationId,String name,Set<String> redirectUris,Set<String> scopes,boolean machine,boolean publicClient) {}
    public record Approval(Set<String> scopes,boolean approve) {}
    private String user(String token) {return tokens.requireFirstPartyUserId(token);}
    private <T> ResponseEntity<T> secret(T value) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public List<TenantDirectory.Tenant> list(@RequestHeader(value="Authorization",required=false) String token) {return tenants.list(user(token));}
    @PostMapping public Map<String,String> create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody TenantInput input) {return Map.of("tenantId",tenants.create(user(token),input.name()));}
    @PutMapping("/{tenant}") public void update(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody TenantInput input) {tenants.update(tenant,user(token),input.name(),input.enabled());}
    @PostMapping("/{tenant}/token") public ResponseEntity<?> tenantToken(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant) {
        String id=user(token);tenants.member(tenant,id);var account=users.findUserByID(id);
        if(account==null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        tenants.audit(tenant,id,"TENANT_TOKEN_REQUESTED",tenant,"{}");
        return secret(Map.of("access_token",tokens.issue(account, Map.of("tenantId", tenant, "tenantVersion", tenants.tokenVersion(tenant,id))),"token_type","Bearer","tenant_id",tenant));
    }
    @GetMapping("/{tenant}/members") public List<TenantDirectory.Member> members(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant) {return tenants.members(tenant,user(token));}
    @GetMapping("/{tenant}/invitation-candidates")
    public ResponseEntity<List<TenantDirectory.InvitationCandidate>> invitationCandidates(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String tenant, @RequestParam(defaultValue = "") String q) {
        return secret(tenants.invitationCandidates(tenant, user(token), q));
    }
    @PostMapping("/{tenant}/invitations") public ResponseEntity<?> invite(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody MemberInput input) {return secret(tenants.invite(tenant,user(token),input.userId(),input.role()));}
    @GetMapping("/{tenant}/invitations") public List<TenantDirectory.PendingInvitation> invitations(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant) {return tenants.invitations(tenant,user(token));}
    @DeleteMapping("/{tenant}/invitations/{invitation}") public void cancelInvitation(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@PathVariable String invitation) {tenants.cancelInvitation(tenant,user(token),invitation);}
    @PostMapping("/{tenant}/invitations/accept") public void accept(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody AcceptInput input) {tenants.accept(tenant,user(token),input.invitationId(),input.token());}
    @PutMapping("/{tenant}/members/{member}") public void role(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@PathVariable String member,@RequestBody MemberInput input) {tenants.changeRole(tenant,user(token),member,input.role());}
    @DeleteMapping("/{tenant}/members/{member}") public void remove(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@PathVariable String member) {tenants.remove(tenant,user(token),member);}
    @PostMapping("/{tenant}/owner") public void transfer(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody MemberInput input) {tenants.transfer(tenant,user(token),input.userId());}
    @GetMapping("/{tenant}/applications") public List<OAuthApplications.TenantClient> applications(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant) {return apps.tenantClients(tenant,user(token));}
    @GetMapping("/{tenant}/catalog") public Map<String,Map<String,String>> catalog(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant) {
        tenants.member(tenant,user(token));Map<String,Map<String,String>> result=new LinkedHashMap<>();
        catalog.getApplications().keySet().forEach(app->result.put(app,catalog.catalog(app)));return result;
    }
    @PostMapping("/{tenant}/applications") public ResponseEntity<?> application(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody ClientInput input) {return secret(apps.createTenant(tenant,user(token),input.applicationId(),input.name(),input.redirectUris(),input.scopes(),input.machine(),input.publicClient()));}
    @PutMapping("/{tenant}/applications/{client}/approval") public void approve(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@PathVariable String client,@RequestBody Approval input) {apps.approve(tenant,user(token),client,input.scopes(),input.approve());}
    @DeleteMapping("/{tenant}/applications/{client}") public void disable(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@PathVariable String client) {apps.disableTenantClient(tenant,user(token),client);}
    @PostMapping("/{tenant}/applications/{client}/secret") public ResponseEntity<?> rotate(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@PathVariable String client) {return secret(apps.rotate(tenant,user(token),client));}
    @PostMapping("/{tenant}/resource-clients") public ResponseEntity<?> resource(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody ClientInput input) {return secret(apps.tenantResource(tenant,user(token),input.applicationId(),input.name()));}
    @GetMapping("/{tenant}/permissions") public List<TenantDirectory.Permission> permissions(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant) {return tenants.permissions(tenant,user(token));}
    @PutMapping("/{tenant}/permissions") public void grant(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody TenantDirectory.Permission input) {validate(input);tenants.permission(tenant,user(token),input,true);}
    @DeleteMapping("/{tenant}/permissions") public void revoke(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestBody TenantDirectory.Permission input) {validate(input);tenants.permission(tenant,user(token),input,false);}
    @GetMapping("/{tenant}/audit") public List<TenantDirectory.Audit> audit(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String tenant,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="") String before) {return tenants.audits(tenant,user(token),limit,before);}
    private void validate(TenantDirectory.Permission input) {if(!catalog.catalog(input.applicationId()).containsKey(input.applicationId()+":"+input.resourceType()+":"+input.action())) throw new IllegalArgumentException("Unknown permission");}
}
