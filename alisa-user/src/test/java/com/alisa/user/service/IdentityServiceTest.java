package com.alisa.user.service;

import com.alisa.user.dto.ChangePasswordRequest;
import com.alisa.user.dto.user.UserDto;
import com.alisa.user.entity.User;
import com.alisa.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityServiceTest {
    UserMapper users;
    VerificationCodeService codes;
    AccessTokenService tokens;
    PasswordService passwords;
    IdentityService service;
    User account;

    @BeforeEach void setup() {
        users = mock(UserMapper.class); codes = mock(VerificationCodeService.class);
        tokens = mock(AccessTokenService.class); passwords = new PasswordService();
        account = new User(); account.setId("0123456789abcdef0123456789abcdef");
        account.setUsername("alice"); account.setEmail("alice@example.test"); account.setPassword("legacy-pass");
        var registry = new com.alisa.user.provider.IdentityProviderRegistry(java.util.List.of(
                new com.alisa.user.provider.LocalIdentityProvider(users, passwords)));
        var login = new ProviderLoginService(registry, mock(com.alisa.user.mapper.ExternalIdentityMapper.class), users, tokens);
        service = new IdentityService(users, passwords, tokens, codes, login);
        when(users.findUserByID(account.getId())).thenReturn(account);
    }
    UserDto login(String password) {
        UserDto request = new UserDto(); request.setUsername("alice"); request.setPassword(password); return request;
    }
    @Test void unknownOrInvalidAccountDoesNotThrowOrIssueAToken() {
        assertEquals("", service.signin(login("wrong")));
        when(users.findUserByName("alice")).thenReturn(account);
        assertEquals("", service.signin(login("wrong")));
        assertEquals("", service.signin(login(null)));
        verifyNoInteractions(tokens);
        verify(users, never()).updatePasswordIfMatches(anyString(), anyString(), anyString());
    }
    @Test void legacyLoginPreservesIdAndUpgradesPasswordAtomically() {
        when(users.findUserByName("alice")).thenReturn(account);
        when(users.updatePasswordIfMatches(eq(account.getId()), eq("legacy-pass"), anyString())).thenReturn(1);
        when(tokens.issue(account)).thenReturn("jwt");
        assertEquals("jwt", service.signin(login("legacy-pass")));
        var encoded = ArgumentCaptor.forClass(String.class);
        verify(users).updatePasswordIfMatches(eq(account.getId()), eq("legacy-pass"), encoded.capture());
        assertTrue(passwords.isEncoded(encoded.getValue()));
        assertTrue(passwords.matches("legacy-pass", encoded.getValue()));
    }
    @Test void concurrentPasswordChangeCannotBeOverwrittenByLegacyLogin() {
        when(users.findUserByName("alice")).thenReturn(account);
        when(users.updatePasswordIfMatches(anyString(), anyString(), anyString())).thenReturn(0);
        assertEquals("", service.signin(login("legacy-pass")));
        verifyNoInteractions(tokens);
    }
    @Test void hashedAccountsSignInWithoutAnotherPasswordWrite() {
        account.setPassword(passwords.encode("new-password"));
        when(users.findUserByName("alice")).thenReturn(account);
        when(tokens.issue(account)).thenReturn("jwt");
        assertEquals("jwt", service.signin(login("new-password")));
        verify(users, never()).updatePasswordIfMatches(anyString(), anyString(), anyString());
    }
    @Test void registrationStoresAHashAndDoesNotContainBusinessData() {
        UserDto request = login("new-password"); request.setEmail(" Alice@Example.test ");
        when(users.saveUser(any())).thenReturn(true);
        assertTrue(service.signup(request));
        var saved = ArgumentCaptor.forClass(User.class); verify(users).saveUser(saved.capture());
        assertEquals(32, saved.getValue().getId().length());
        assertEquals("alice@example.test", saved.getValue().getEmail());
        assertTrue(passwords.matches("new-password", saved.getValue().getPassword()));
        assertNotEquals("new-password", saved.getValue().getPassword());
    }
    @Test void duplicateRegistrationNeverUpdatesTheExistingAccount() {
        when(users.findUserByName("alice")).thenReturn(account);
        UserDto request = login("new-password"); request.setEmail("alice@example.test");
        assertFalse(service.signup(request));
        verify(users, never()).saveUser(any());
    }
    @Test void emailResetUsesTheEmailLookupAndAConsumedCode() {
        when(users.findUserByEmail("alice@example.test")).thenReturn(account);
        when(codes.consume("alice@example.test", "123456")).thenReturn(true);
        when(users.updatePasswordIfMatches(eq(account.getId()), eq("legacy-pass"), anyString())).thenReturn(1);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setEmail("alice@example.test"); request.setCode("123456"); request.setNewPassword("a-new-password");
        assertTrue(service.changePassword(request)); // Also supports the existing client's route.
        verify(users, never()).findUserByName(anyString());
        var encoded = ArgumentCaptor.forClass(String.class);
        verify(users).updatePasswordIfMatches(eq(account.getId()), eq("legacy-pass"), encoded.capture());
        assertTrue(passwords.matches("a-new-password", encoded.getValue()));
    }
    @Test void invalidResetCodeCannotChangeThePassword() {
        when(users.findUserByEmail("alice@example.test")).thenReturn(account);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setEmail("alice@example.test"); request.setCode("000000"); request.setNewPassword("new-password");
        assertFalse(service.resetPassword(request));
        verify(users, never()).updatePasswordIfMatches(anyString(), anyString(), anyString());
    }
    @Test void profileUsesTheSignedIdAndCannotReadAnotherAccount() {
        when(tokens.requireFirstPartyUserId("Bearer jwt")).thenReturn(account.getId());
        when(users.findUserByID(account.getId())).thenReturn(account);
        assertEquals("alice@example.test", service.profile("Bearer jwt", null).getEmail());
        var exception = assertThrows(ResponseStatusException.class, () -> service.profile("Bearer jwt", "bob"));
        assertEquals(403, exception.getStatusCode().value());
    }
    @Test void nonexistentEmailDoesNotTriggerMailOrRevealAccountPresence() {
        service.sendResetCode("missing@example.test");
        verifyNoInteractions(codes);
    }
}
