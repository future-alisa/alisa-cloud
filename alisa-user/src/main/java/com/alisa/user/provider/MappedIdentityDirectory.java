package com.alisa.user.provider;

import java.util.List;
import com.alisa.identity.ExternalIdentityDirectory;
import com.alisa.identity.VerifiedIdentity;
import com.alisa.user.mapper.ExternalIdentityMapper;
import org.springframework.stereotype.Component;

@Component
public final class MappedIdentityDirectory implements ExternalIdentityDirectory {
    private final ExternalIdentityMapper links;
    public MappedIdentityDirectory(ExternalIdentityMapper links) { this.links = links; }

    @Override
    public List<VerifiedIdentity> findForUser(String internalUserId, String identityProviderId) {
        if (internalUserId == null || internalUserId.isBlank() || identityProviderId == null || identityProviderId.isBlank())
            throw new IllegalArgumentException("Missing identity lookup scope");
        return links.findByUserId(internalUserId).stream()
                .filter(link -> identityProviderId.equals(link.getProviderId()))
                .map(link -> new VerifiedIdentity(link.getIssuer(), link.getSubject())).toList();
    }
}
