package com.alisa.authorization.model;

import java.util.Objects;

public record AuthorizationDecision(Effect effect, String reason) {
    public enum Effect { PERMIT, DENY, INDETERMINATE }

    public AuthorizationDecision {
        Objects.requireNonNull(effect, "effect");
        Fields.text(reason, "decision reason", 128);
    }

    public boolean allowed() { return effect == Effect.PERMIT; }
    public static AuthorizationDecision permit() { return new AuthorizationDecision(Effect.PERMIT, "GRANTED"); }
    public static AuthorizationDecision deny(String reason) { return new AuthorizationDecision(Effect.DENY, reason); }
    public static AuthorizationDecision unavailable() {
        return new AuthorizationDecision(Effect.INDETERMINATE, "PROVIDER_UNAVAILABLE");
    }
}
