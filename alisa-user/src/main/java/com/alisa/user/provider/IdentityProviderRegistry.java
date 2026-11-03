package com.alisa.user.provider;

import java.util.*;
import com.alisa.identity.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class IdentityProviderRegistry {
    public record Descriptor(String id, Set<String> methods) {}
    private final Map<String, IdentityProvider> providers;
    private final Map<String, Set<String>> methods;

    public IdentityProviderRegistry(List<IdentityProvider> implementations) {
        Map<String, IdentityProvider> registered = new LinkedHashMap<>();
        Map<String, Set<String>> supported = new LinkedHashMap<>();
        for (IdentityProvider provider : implementations) {
            String id = provider.id();
            if (id == null || id.isBlank() || id.length() > 64)
                throw new IllegalArgumentException("Invalid identity provider id");
            if (registered.putIfAbsent(id, provider) != null)
                throw new IllegalArgumentException("Duplicate identity provider: " + id);
            Set<String> declared = Set.copyOf(provider.methods());
            if (declared.isEmpty() || declared.stream().anyMatch(m -> m.isBlank() || m.length() > 64))
                throw new IllegalArgumentException("Invalid authentication methods");
            supported.put(id, declared);
        }
        providers = Map.copyOf(registered);
        methods = Map.copyOf(supported);
    }

    public List<Descriptor> descriptors() {
        return methods.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new Descriptor(entry.getKey(), entry.getValue())).toList();
    }

    public VerifiedIdentity authenticate(String providerId, IdentityCredentials credentials) {
        return authenticate(providerId, credentials, IdentityAuthenticationContext.signIn());
    }

    public VerifiedIdentity authenticate(String providerId, IdentityCredentials credentials, IdentityAuthenticationContext context) {
        IdentityProvider provider = providers.get(providerId);
        if (provider == null || !methods.get(providerId).contains(credentials.method()))
            throw new AuthenticationRejectedException();
        try {
            VerifiedIdentity identity = provider.authenticate(credentials, context);
            if (identity == null) throw new AuthenticationRejectedException();
            return identity;
        } catch (AuthenticationRejectedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "认证提供者暂不可用");
        }
    }
}
