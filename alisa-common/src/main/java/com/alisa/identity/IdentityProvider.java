package com.alisa.identity;

import java.util.Set;

/**
 * Authentication extension point. Implementations must verify proofs, including
 * protocol state/nonce/PKCE where applicable, before returning an identity.
 * Implementations are registered as Spring beans by their integration module.
 */
public interface IdentityProvider {
    String id();
    Set<String> methods();
    VerifiedIdentity authenticate(IdentityCredentials credentials, IdentityAuthenticationContext context);
}
