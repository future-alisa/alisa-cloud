package com.alisa.authorization;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationEngineTest {
    private AuthorizationRequest request(String app) {
        return new AuthorizationRequest(app, AuthorizationSubject.user("alice"),
                new AuthorizationResource("document", "42"), "read", Map.of());
    }
    private AuthorizationProvider provider(String id, java.util.function.Function<AuthorizationRequest, AuthorizationDecision> decide) {
        return new AuthorizationProvider() {
            public String id() { return id; }
            public AuthorizationDecision decide(AuthorizationRequest request) { return decide.apply(request); }
        };
    }
    @Test void aNewProviderWorksWithoutAnyVendorBranchInTheEngine() {
        var external = provider("custom-company-policy", request -> {
            assertEquals("alice", request.subject().id());
            assertEquals("42", request.resource().id());
            return AuthorizationDecision.permit();
        });
        var engine = new AuthorizationEngine(List.of(external), Map.of("app", external.id()));
        assertTrue(engine.evaluate(request("app")).allowed());
    }
    @Test void unknownApplicationsAreDeniedWithoutConsultingProviders() {
        var engine = new AuthorizationEngine(List.of(provider("remote", r -> { fail(); return null; })), Map.of());
        assertEquals("UNKNOWN_APPLICATION", engine.evaluate(request("unconfigured")).reason());
    }
    @Test void providerFailureNeverFallsBackToTheLocalAllowPolicy() {
        AtomicInteger localCalls = new AtomicInteger();
        var local = provider("local", r -> { localCalls.incrementAndGet(); return AuthorizationDecision.permit(); });
        var remote = provider("remote", r -> { throw new IllegalStateException("unavailable"); });
        var engine = new AuthorizationEngine(List.of(local, remote), Map.of("app", "remote"));
        var decision = engine.evaluate(request("app"));
        assertFalse(decision.allowed());
        assertEquals(AuthorizationDecision.Effect.INDETERMINATE, decision.effect());
        assertEquals(0, localCalls.get());
    }
    @Test void nullDecisionsAreNeverAnImplicitPermit() {
        var engine = new AuthorizationEngine(List.of(provider("broken", r -> null)), Map.of("app", "broken"));
        assertFalse(engine.evaluate(request("app")).allowed());
    }
    @Test void duplicateProvidersAndUnregisteredConfigurationFailFast() {
        var provider = provider("one", r -> AuthorizationDecision.permit());
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationEngine(List.of(provider, provider), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationEngine(List.of(provider), Map.of("app", "missing")));
    }
    @Test void aReadOnlyExternalProviderCannotAccidentallyMutateLocalPolicies() {
        var engine = new AuthorizationEngine(List.of(provider("remote", r -> AuthorizationDecision.permit())), Map.of("app", "remote"));
        assertThrows(UnsupportedOperationException.class, () -> engine.administration("app"));
    }
}
