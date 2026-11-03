package com.alisa.user.provider;

import java.util.List;
import com.alisa.user.entity.ExternalIdentityLink;
import com.alisa.user.mapper.ExternalIdentityMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MappedIdentityDirectoryTest {
    ExternalIdentityLink link(String provider, String subject) {
        ExternalIdentityLink link = new ExternalIdentityLink();
        link.setProviderId(provider); link.setIssuer("issuer"); link.setSubject(subject); return link;
    }
    @Test void lookupUsesTheInternalUserAndTheSelectedIdentityProvider() {
        var mapper = mock(ExternalIdentityMapper.class);
        when(mapper.findByUserId("alice")).thenReturn(List.of(link("company-a", "first"), link("company-b", "second")));
        var directory = new MappedIdentityDirectory(mapper);
        var identities = directory.findForUser("alice", "company-b");
        assertEquals(1, identities.size());
        assertEquals("second", identities.get(0).subject());
        verify(mapper).findByUserId("alice");
    }
    @Test void unknownMappingsDoNotInventAnExternalSubject() {
        var mapper = mock(ExternalIdentityMapper.class);
        when(mapper.findByUserId("alice")).thenReturn(List.of());
        assertTrue(new MappedIdentityDirectory(mapper).findForUser("alice", "company").isEmpty());
    }
}
