package com.alisa.user.provider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alisa.identity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityProviderRegistryTest {
    IdentityProvider provider(String id) {
        return new IdentityProvider() {
            public String id() { return id; }
            public Set<String> methods() { return Set.of("proof-v1"); }
            public VerifiedIdentity authenticate(IdentityCredentials credentials, IdentityAuthenticationContext context) {
                if (!"verified-proof".equals(credentials.values().get("proof"))) throw new AuthenticationRejectedException();
                return new VerifiedIdentity("https://issuer.example", "stable-subject");
            }
        };
    }
    @Test void arbitraryProvidersCanBeRegisteredWithoutChangingTheRegistry() {
        var registry = new IdentityProviderRegistry(List.of(provider("company-idp")));
        var identity = registry.authenticate("company-idp", new IdentityCredentials("proof-v1", Map.of("proof", "verified-proof")));
        assertEquals("stable-subject", identity.subject());
        assertEquals("company-idp", registry.descriptors().get(0).id());
    }
    @Test void missingUnsupportedAndUnverifiedProofsAreRejected() {
        var registry = new IdentityProviderRegistry(List.of(provider("company-idp")));
        assertThrows(AuthenticationRejectedException.class, () -> registry.authenticate("unknown", new IdentityCredentials("proof-v1", Map.of())));
        assertThrows(AuthenticationRejectedException.class, () -> registry.authenticate("company-idp", new IdentityCredentials("password", Map.of())));
        assertThrows(AuthenticationRejectedException.class, () -> registry.authenticate("company-idp", new IdentityCredentials("proof-v1", Map.of("proof", "unverified"))));
    }
    @Test void duplicateIdsFailFastAndCredentialsAreRedacted() {
        assertThrows(IllegalArgumentException.class, () -> new IdentityProviderRegistry(List.of(provider("one"), provider("one"))));
        assertFalse(new IdentityCredentials("password", Map.of("password", "do-not-log")).toString().contains("do-not-log"));
    }
}
