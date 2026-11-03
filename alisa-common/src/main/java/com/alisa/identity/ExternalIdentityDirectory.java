package com.alisa.identity;

import java.util.List;

/**
 * Read-only mapping port for integrations that need external subjects.
 * Adapter libraries depend on this contract, not on the executable user-service JAR.
 */
public interface ExternalIdentityDirectory {
    List<VerifiedIdentity> findForUser(String internalUserId, String identityProviderId);
}
