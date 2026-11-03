package com.alisa.authorization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.*;

public final class AuthorizationEngine {
    private static final Logger LOG = Logger.getLogger(AuthorizationEngine.class.getName());
    private final Map<String, AuthorizationProvider> providers;
    private final Map<String, String> applicationProviders;

    public AuthorizationEngine(Collection<AuthorizationProvider> implementations, Map<String, String> applicationProviders) {
        Map<String, AuthorizationProvider> registered = new LinkedHashMap<>();
        for (AuthorizationProvider provider : implementations) {
            String id = Fields.text(provider.id(), "authorization provider id", 64);
            if (registered.putIfAbsent(id, provider) != null)
                throw new IllegalArgumentException("Duplicate authorization provider: " + id);
        }
        this.providers = Map.copyOf(registered);
        this.applicationProviders = Map.copyOf(applicationProviders);
        this.applicationProviders.forEach((app, provider) -> {
            Fields.text(app, "application id", 64);
            if (!providers.containsKey(provider))
                throw new IllegalArgumentException("Unregistered authorization provider for application: " + app);
        });
    }

    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        String providerId = applicationProviders.get(request.applicationId());
        if (providerId == null) return AuthorizationDecision.deny("UNKNOWN_APPLICATION");
        try {
            AuthorizationDecision decision = providers.get(providerId).decide(request);
            return decision == null ? AuthorizationDecision.unavailable() : decision;
        } catch (RuntimeException exception) {
            LOG.warning("Authorization provider unavailable: " + providerId);
            return AuthorizationDecision.unavailable();
        }
    }

    public PolicyAdministration administration(String applicationId) {
        String providerId = applicationProviders.get(applicationId);
        if (providerId == null) throw new IllegalArgumentException("Unknown application");
        AuthorizationProvider provider = providers.get(providerId);
        if (provider instanceof PolicyAdministration administration) return administration;
        throw new UnsupportedOperationException("Selected provider does not support policy administration");
    }
}
