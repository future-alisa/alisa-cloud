package com.alisa.user.controller;

import java.util.List;
import com.alisa.identity.*;
import com.alisa.util.Result;
import com.alisa.user.entity.ExternalIdentityLink;
import com.alisa.user.provider.IdentityProviderRegistry;
import com.alisa.user.service.ProviderLoginService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class IdentityProviderController {
    private final IdentityProviderRegistry providers;
    private final ProviderLoginService login;
    public IdentityProviderController(IdentityProviderRegistry providers, ProviderLoginService login) {
        this.providers = providers;
        this.login = login;
    }
    @GetMapping("/providers")
    public Result<List<IdentityProviderRegistry.Descriptor>> providers() { return new Result<>(providers.descriptors()); }

    @PostMapping("/signin/{providerId}")
    public Result<String> signin(@PathVariable String providerId, @RequestBody IdentityCredentials credentials) {
        return new Result<>(login.signin(providerId, credentials));
    }

    @GetMapping("/identities")
    public Result<List<ExternalIdentityLink>> bindings(@RequestHeader(value="Authorization",required=false) String token) {
        return new Result<>(login.bindings(token));
    }

    @PostMapping("/identities/{providerId}")
    public Result<Boolean> bind(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String providerId, @RequestBody IdentityCredentials credentials) {
        login.bind(token, providerId, credentials);
        return new Result<>(true);
    }

    @DeleteMapping("/identities/{providerId}")
    public Result<Boolean> unbind(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable String providerId, @RequestBody VerifiedIdentity identity) {
        return new Result<>(login.unbind(token, providerId, identity));
    }
}
