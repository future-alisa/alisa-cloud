package com.alisa.identity;

/** An issuer-scoped stable subject, never an email-address based account link. */
public record VerifiedIdentity(String issuer, String subject) {
    public VerifiedIdentity {
        if (issuer == null || issuer.isBlank() || issuer.length() > 255
                || subject == null || subject.isBlank() || subject.length() > 255)
            throw new IllegalArgumentException("Invalid verified identity");
    }
}
