package com.alisa.user.controller;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.alisa.authorization.*;
import com.alisa.authorization.config.AuthorizationProperties;
import com.alisa.authorization.local.LocalAuthorizationProvider;
import com.alisa.authorization.model.*;
import com.alisa.authorization.spi.*;
import com.alisa.user.service.AccessTokenService;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthorizationControllerTest {
    AccessTokenService tokens;
    AuthorizationProperties properties;
    @BeforeEach void setup() {
        tokens = mock(AccessTokenService.class);
        when(tokens.identity("Bearer user")).thenReturn(com.alisa.security.RequestIdentity.firstParty("alice","Alice"));
        when(tokens.identity("Bearer admin")).thenReturn(com.alisa.security.RequestIdentity.firstParty("administrator","Admin"));
        when(tokens.identity(isNull())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        when(tokens.requireFirstPartyUserId("Bearer user")).thenReturn("alice");
        when(tokens.requireFirstPartyUserId("Bearer admin")).thenReturn("administrator");
        properties = new AuthorizationProperties();
        properties.setAdministrators(Set.of("administrator"));
    }
    MockMvc mvc(AuthorizationProvider provider) {
        var engine = new AuthorizationEngine(List.of(provider), Map.of("app", provider.id()));
        return MockMvcBuilders.standaloneSetup(new AuthorizationController(engine, properties, tokens))
                .setControllerAdvice(new IdentityExceptionHandler()).build();
    }
    @Test void checkDerivesTheSubjectFromTheTokenAndRejectsAnonymousCalls() throws Exception {
        AtomicReference<AuthorizationRequest> received = new AtomicReference<>();
        var mvc = mvc(new AuthorizationProvider() {
            public String id() { return "external"; }
            public AuthorizationDecision decide(AuthorizationRequest request) { received.set(request); return AuthorizationDecision.permit(); }
        });
        String body = "{\"applicationId\":\"app\",\"resourceType\":\"file\",\"resourceId\":\"42\",\"action\":\"read\",\"subjectId\":\"administrator\"}";
        mvc.perform(post("/api/v1/authorization/check").header("Authorization", "Bearer user")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effect").value("PERMIT"));
        assertEquals("alice", received.get().subject().id());
        mvc.perform(post("/api/v1/authorization/check").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
    @Test void ordinaryUsersCannotReadOrModifyAuthorizationPolicy() throws Exception {
        var store = mock(AuthorizationPolicyStore.class);
        var mvc = mvc(new LocalAuthorizationProvider(store));
        mvc.perform(put("/api/v1/authorization/applications/app/roles/admin")
                .header("Authorization", "Bearer user").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"attempt\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(store);
    }
    @Test void bootstrapAdministratorsCanManagePoliciesButCannotChooseAnotherApplication() throws Exception {
        var store = mock(AuthorizationPolicyStore.class);
        var mvc = mvc(new LocalAuthorizationProvider(store));
        mvc.perform(put("/api/v1/authorization/applications/app/roles/editor").header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Editor\"}")).andExpect(status().isOk());
        verify(store).saveRole(new PolicyRole("app", "editor", "Editor"));
        mvc.perform(put("/api/v1/authorization/applications/unknown/roles/editor").header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Editor\"}")).andExpect(status().isBadRequest());
    }
    @Test void failingExternalProvidersReturnUnavailableRatherThanPermissionGranted() throws Exception {
        var mvc = mvc(new AuthorizationProvider() {
            public String id() { return "external"; }
            public AuthorizationDecision decide(AuthorizationRequest request) { throw new IllegalStateException(); }
        });
        mvc.perform(post("/api/v1/authorization/check").header("Authorization", "Bearer user")
                .contentType(MediaType.APPLICATION_JSON).content("{\"applicationId\":\"app\",\"resourceType\":\"file\",\"resourceId\":\"42\",\"action\":\"read\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.effect").value("INDETERMINATE"));
        mvc.perform(put("/api/v1/authorization/applications/app/roles/editor").header("Authorization", "Bearer admin")
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Editor\"}")).andExpect(status().isNotImplemented());
    }
}
