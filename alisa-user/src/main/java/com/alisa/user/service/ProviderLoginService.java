package com.alisa.user.service;

import java.util.List;
import java.util.Map;
import com.alisa.identity.*;
import com.alisa.user.entity.*;
import com.alisa.user.mapper.*;
import com.alisa.user.provider.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderLoginService {
    private final IdentityProviderRegistry providers;
    private final ExternalIdentityMapper links;
    private final UserMapper users;
    private final AccessTokenService tokens;

    public ProviderLoginService(IdentityProviderRegistry providers, ExternalIdentityMapper links,
            UserMapper users, AccessTokenService tokens) {
        this.providers = providers;
        this.links = links;
        this.users = users;
        this.tokens = tokens;
    }

    public String signin(String providerId, IdentityCredentials credentials) {
        VerifiedIdentity identity = providers.authenticate(providerId, credentials);
        String userId;
        if (LocalIdentityProvider.ID.equals(providerId)) {
            if (!LocalIdentityProvider.ISSUER.equals(identity.issuer())) throw new AuthenticationRejectedException();
            userId = identity.subject();
        } else {
            userId = links.findUserId(providerId, identity.issuer(), identity.subject());
            if (userId == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "第三方身份尚未绑定，请先登录已有账号后绑定");
        }
        User user = users.findUserByID(userId);
        if (user == null) throw new AuthenticationRejectedException();
        return tokens.issue(user);
    }

    public void bind(String authorization, String providerId, IdentityCredentials credentials) {
        String userId = requireExistingUser(authorization);
        if (LocalIdentityProvider.ID.equals(providerId))
            throw new IllegalArgumentException("本地账号无需绑定");
        VerifiedIdentity identity;
        try {
            identity = providers.authenticate(providerId, credentials, IdentityAuthenticationContext.link(userId));
        } catch (AuthenticationRejectedException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "提供者身份凭据验证失败");
        }
        String existing = links.findUserId(providerId, identity.issuer(), identity.subject());
        if (existing != null) {
            if (!existing.equals(userId)) throw conflict();
            return;
        }
        ExternalIdentityLink link = new ExternalIdentityLink();
        link.setProviderId(providerId);
        link.setIssuer(identity.issuer());
        link.setSubject(identity.subject());
        link.setUserId(userId);
        try {
            links.insertLink(link);
        } catch (DuplicateKeyException exception) {
            // A racing request can never reassign an identity to another account.
            if (!userId.equals(links.findUserId(providerId, identity.issuer(), identity.subject()))) throw conflict();
        }
    }

    public List<ExternalIdentityLink> bindings(String authorization) {
        return links.findByUserId(requireExistingUser(authorization));
    }

    public boolean unbind(String authorization, String providerId, VerifiedIdentity identity) {
        String userId = requireExistingUser(authorization);
        User user = users.findUserByID(userId);
        if (user == null) throw new AuthenticationRejectedException();
        if (user.getPassword() == null || user.getPassword().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先设置可用的本地登录凭据");
        return links.deleteOwned(providerId, identity.issuer(), identity.subject(), userId) == 1;
    }

    private String requireExistingUser(String authorization) {
        String userId = tokens.requireFirstPartyUserId(authorization);
        if (users.findUserByID(userId) == null) throw new AuthenticationRejectedException();
        return userId;
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "该身份已绑定其他账号");
    }
}
