package com.alisa.user.oauth;

import com.alisa.authorization.config.AuthorizationProperties;
import com.alisa.user.service.AccessTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(prefix="alisa.oauth",name="enabled",havingValue="true")
public class OAuthResourceClients {
    private final OAuthApplications applications;
    private final AccessTokenService tokens;
    private final AuthorizationProperties authorization;
    public OAuthResourceClients(OAuthApplications applications,AccessTokenService tokens,AuthorizationProperties authorization) {
        this.applications=applications;this.tokens=tokens;this.authorization=authorization;
    }
    public record Input(String applicationId,String name) {}
    @PostMapping("/api/v1/user/oauth/resource-clients")
    public ResponseEntity<OAuthApplications.Created> create(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Input input) {
        String owner=tokens.requireFirstPartyUserId(token);
        if(!authorization.getAdministrators().contains(owner)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(input.name()==null || input.name().isBlank() || input.name().length()>100) throw new IllegalArgumentException("Invalid resource server name");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(applications.resourceServer(owner,input.applicationId(),input.name()));
    }
}
