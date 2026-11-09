package com.alisa.user.service;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.ObjectProvider;
import com.alisa.user.tenant.OrganizationDirectory;
import com.alisa.util.JwtUtil;
import com.alisa.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@Component
public class AccessTokenService {
    private static final String SESSION_VERSION = "managedAccountVersion";
    private static final String BROWSER_VERSION = "ALISA_ACCOUNT_VERSION_";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOrganizationAccounts(ObjectProvider<OrganizationDirectory> organizations) {
        this.organizations = organizations;
    }

    private ObjectProvider<OrganizationDirectory> organizations;

    public String accountVersion(String userId) {
        var directory = organizations == null ? null : organizations.getIfAvailable();
        return directory == null ? "0" : directory.activeSessionVersion(userId);
    }

    public String browserVersionAuthority(String userId) {
        return BROWSER_VERSION + accountVersion(userId);
    }

    public void requireBrowserSession(Authentication authentication) {
        String version = authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(BROWSER_VERSION))
            .map(authority -> authority.substring(BROWSER_VERSION.length()))
            .findFirst().orElse("0");
        requireVersion(authentication.getName(), version);
    }

    private void requireVersion(String userId, String version) {
        if (!Objects.equals(accountVersion(userId), version == null ? "0" : version))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
    }

    public String issue(User user) {
        return issue(user, Map.of());
    }

    public String issue(User user, Map<String, String> additionalClaims) {
        Map<String, Object> claims = new HashMap<>(additionalClaims);
        claims.put("userId", user.getId());
        claims.put(SESSION_VERSION, accountVersion(user.getId()));
        return JwtUtil.GenerateToken(user.getUsername(), claims);
    }
    private com.alisa.security.DelegatedIdentityResolver delegated;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setDelegated(com.alisa.security.DelegatedIdentityResolver value) { delegated=value; }
    public com.alisa.security.RequestIdentity identity(String authorization) {
        if(authorization!=null && authorization.startsWith("Bearer ") && authorization.substring(7).indexOf('.')<0 && delegated!=null) {
            try { return delegated.resolve(authorization.substring(7)); } catch(com.alisa.security.AccessFailure ex) { throw new ResponseStatusException(HttpStatus.valueOf(ex.status()),ex.getMessage()); }
        }
        String id=firstParty(authorization);
        var claims=JwtUtil.extractAllClaims(authorization.substring(7));
        return new com.alisa.security.RequestIdentity(id,claims.getSubject(),false,null,null,java.util.Set.of(),claims.get("tenantId",String.class),false,claims.get("tenantVersion",String.class));
    }
    public String requireUserId(String authorization) {
        var identity=identity(authorization);
        if(identity.application()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"需要用户身份");
        return identity.userId();
    }
    public void requireScope(String authorization,String app,String type,String action) {
        var identity=identity(authorization);
        if(identity.delegated() && (!java.util.Objects.equals(app,identity.applicationId()) || !identity.scopes().contains(app+":"+type+":"+action)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"授权范围不足");
    }
    public String requireFirstPartyUserId(String authorization) { return firstParty(authorization); }
    private String firstParty(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            var claims = JwtUtil.extractAllClaims(authorization.substring(7));
            String id = claims.get("userId", String.class);
            if (!StringUtils.hasText(id) || !StringUtils.hasText(claims.getSubject())) throw new IllegalArgumentException();
            requireVersion(id, claims.get(SESSION_VERSION, String.class));
            return id;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
    }
}
