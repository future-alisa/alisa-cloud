package com.alisa.user.tenant;

import com.alisa.user.service.AccessTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user/organizations")
@ConditionalOnProperty(prefix = "alisa.tenancy", name = "enabled", havingValue = "true")
public class OrganizationController {
    private final OrganizationDirectory directory;
    private final AccessTokenService tokens;

    public OrganizationController(OrganizationDirectory directory, AccessTokenService tokens) {
        this.directory = directory;
        this.tokens = tokens;
    }

    public record CreateOrganization(String name) {}
    public record CreateAccount(String username, String email) {}
    public record ActivationLink(String id, String token) {}
    public record ActivateAccount(String id, String token, String password, String code) {}
    public record TenantAssignment(String tenantId) {}

    @GetMapping
    public List<OrganizationDirectory.Organization> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return directory.list(tokens.requireFirstPartyUserId(token));
    }

    @PostMapping
    public OrganizationDirectory.Organization create(@RequestHeader(value = "Authorization", required = false) String token,
                                                      @RequestBody CreateOrganization input) {
        return directory.create(tokens.requireFirstPartyUserId(token), input.name());
    }

    @GetMapping("/{organization}/accounts")
    public List<OrganizationDirectory.Account> accounts(@RequestHeader(value = "Authorization", required = false) String token,
                                                         @PathVariable String organization) {
        return directory.accounts(organization, tokens.requireFirstPartyUserId(token));
    }

    @PostMapping("/{organization}/accounts")
    public ResponseEntity<OrganizationDirectory.Activation> issue(@RequestHeader(value = "Authorization", required = false) String token,
                                                                   @PathVariable String organization, @RequestBody CreateAccount input) {
        var activation = directory.issue(organization, tokens.requireFirstPartyUserId(token), input.username(), input.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activation);
    }

    @DeleteMapping("/{organization}/accounts/{account}")
    public void cancel(@RequestHeader(value = "Authorization", required = false) String token,
                       @PathVariable String organization, @PathVariable String account) {
        directory.cancel(organization, tokens.requireFirstPartyUserId(token), account);
    }

    @PostMapping("/activation/code")
    public void sendCode(@RequestBody ActivationLink input) {
        directory.sendActivationCode(input.id(), input.token());
    }

    @PostMapping("/activation")
    public void activate(@RequestBody ActivateAccount input) {
        directory.activate(input.id(), input.token(), input.password(), input.code());
    }

    @GetMapping("/{organization}/tenants")
    public List<OrganizationDirectory.LinkedTenant> tenants(@RequestHeader(value = "Authorization", required = false) String token,
                                                            @PathVariable String organization) {
        return directory.linkedTenants(organization, tokens.requireFirstPartyUserId(token));
    }

    @PostMapping("/{organization}/tenants")
    public void link(@RequestHeader(value = "Authorization", required = false) String token,
                     @PathVariable String organization, @RequestBody TenantAssignment input) {
        directory.link(organization, tokens.requireFirstPartyUserId(token), input.tenantId());
    }

    @PostMapping("/{organization}/accounts/{account}/tenants")
    public void assign(@RequestHeader(value = "Authorization", required = false) String token, @PathVariable String organization,
                       @PathVariable String account, @RequestBody TenantAssignment input) {
        directory.assign(organization, tokens.requireFirstPartyUserId(token), account, input.tenantId());
    }
}
