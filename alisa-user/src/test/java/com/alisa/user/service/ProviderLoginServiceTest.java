package com.alisa.user.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alisa.identity.*;
import com.alisa.user.entity.User;
import com.alisa.user.mapper.*;
import com.alisa.user.provider.IdentityProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderLoginServiceTest {
    ExternalIdentityMapper links;
    UserMapper users;
    AccessTokenService tokens;
    ProviderLoginService login;
    IdentityAuthenticationContext lastContext;
    IdentityCredentials proof = new IdentityCredentials("proof", Map.of("value", "valid"));
    @BeforeEach void setup() {
        links = mock(ExternalIdentityMapper.class); users = mock(UserMapper.class); tokens = mock(AccessTokenService.class);
        IdentityProvider external = new IdentityProvider() {
            public String id() { return "company"; }
            public Set<String> methods() { return Set.of("proof"); }
            public VerifiedIdentity authenticate(IdentityCredentials credentials, IdentityAuthenticationContext context) {
                lastContext = context;
                if (!"valid".equals(credentials.values().get("value"))) throw new AuthenticationRejectedException();
                return new VerifiedIdentity("https://issuer.example", "subject-1");
            }
        };
        login = new ProviderLoginService(new IdentityProviderRegistry(List.of(external)), links, users, tokens);
    }
    User account(String id) {
        User user = new User(); user.setId(id); user.setUsername("alice"); user.setPassword("local-credential");
        when(users.findUserByID(id)).thenReturn(user); return user;
    }
    @Test void externalIdentityMustBeExplicitlyLinkedBeforeLogin() {
        assertThrows(ResponseStatusException.class, () -> login.signin("company", proof));
        verifyNoInteractions(tokens);
        verify(users, never()).findUserByEmail(anyString());
    }
    @Test void linkedIdentityResolvesToTheExistingInternalId() {
        User user = account("existing-id");
        when(links.findUserId("company", "https://issuer.example", "subject-1")).thenReturn("existing-id");
        when(tokens.issue(user)).thenReturn("internal-token");
        assertEquals("internal-token", login.signin("company", proof));
        assertEquals(IdentityAuthenticationContext.Purpose.SIGN_IN, lastContext.purpose());
        assertNull(lastContext.userId());
    }
    @Test void bindingRequiresAnAuthenticatedInternalUserAndVerifiedProviderProof() {
        account("alice-id"); when(tokens.requireFirstPartyUserId("Bearer alice")).thenReturn("alice-id");
        login.bind("Bearer alice", "company", new IdentityCredentials("proof",
                Map.of("value", "valid", "userId", "someone-else", "purpose", "SIGN_IN")));
        assertEquals(IdentityAuthenticationContext.Purpose.LINK, lastContext.purpose());
        assertEquals("alice-id", lastContext.userId());
        var saved = ArgumentCaptor.forClass(com.alisa.user.entity.ExternalIdentityLink.class);
        verify(links).insertLink(saved.capture());
        assertEquals("alice-id", saved.getValue().getUserId());
        assertEquals("https://issuer.example", saved.getValue().getIssuer());
    }
    @Test void anIdentityCannotBeReassignedToAnotherUser() {
        account("alice-id"); when(tokens.requireFirstPartyUserId("Bearer alice")).thenReturn("alice-id");
        when(links.findUserId("company", "https://issuer.example", "subject-1")).thenReturn("other-id");
        var exception = assertThrows(ResponseStatusException.class, () -> login.bind("Bearer alice", "company", proof));
        assertEquals(409, exception.getStatusCode().value());
        verify(links, never()).insertLink(any());
    }
    @Test void unlinkingIsAlwaysScopedToTheAuthenticatedAccount() {
        account("alice-id"); when(tokens.requireFirstPartyUserId("Bearer alice")).thenReturn("alice-id");
        login.unbind("Bearer alice", "company", new VerifiedIdentity("https://issuer.example", "subject-1"));
        verify(links).deleteOwned("company", "https://issuer.example", "subject-1", "alice-id");
    }
    @Test void badLinkProofDoesNotInvalidateTheExistingAuthenticatedSession() {
        account("alice-id"); when(tokens.requireFirstPartyUserId("Bearer alice")).thenReturn("alice-id");
        var exception = assertThrows(ResponseStatusException.class, () ->
                login.bind("Bearer alice", "company", new IdentityCredentials("proof", Map.of("value", "invalid"))));
        assertEquals(422, exception.getStatusCode().value());
        verify(links, never()).insertLink(any());
    }
}
