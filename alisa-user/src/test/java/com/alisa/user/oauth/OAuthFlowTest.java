package com.alisa.user.oauth;

import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.sql.DataSource;
import com.alisa.user.provider.IdentityProviderRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringJUnitWebConfig(OAuthFlowTest.Config.class)
@TestPropertySource(properties={"alisa.oauth.enabled=true","alisa.oauth.issuer=http://localhost","alisa.oauth.allow-loopback-redirects=true",
    "alisa.oauth.applications.demo.[demo\\:target\\:read]=Read targets","alisa.oauth.applications.demo.[demo\\:target\\:delete]=Delete targets"})
class OAuthFlowTest {
    @org.springframework.boot.test.context.TestConfiguration @EnableWebSecurity @EnableWebMvc @Import({OAuthConfiguration.class,OAuthPages.class,OAuthLoginPage.class})
    static class Config {
        @Bean DataSource dataSource() {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            new ResourceDatabasePopulator(new ClassPathResource("sql/oauth.sql")).execute(ds);return ds;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean IdentityProviderRegistry providers() { return new IdentityProviderRegistry(List.of()); }
        @Bean com.alisa.authorization.AuthorizationEngine engine() { return new com.alisa.authorization.AuthorizationEngine(List.of(),Map.of()); }
    }
    @Autowired WebApplicationContext context;
    @Autowired OAuthApplications apps;
    @Autowired OAuth2AuthorizationService authorizations;
    @Autowired com.alisa.security.DelegatedIdentityResolver identities;
    MockMvc mvc;
    ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void consentLimitsScopesAndRevocationInvalidatesTokens() throws Exception {
        var app=apps.create("developer","demo","Example",Set.of("http://localhost/callback"),Set.of("demo:target:read","demo:target:delete"),false);
        var resource=apps.resourceServer("admin","demo","API");
        String verifier="abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var start=mvc.perform(get("/oauth2/authorize").with(user("alice"))
            .param("response_type","code").param("client_id",app.clientId()).param("redirect_uri","http://localhost/callback")
            .param("scope","demo:target:read demo:target:delete").param("state","caller-state")
            .param("code_challenge",challenge).param("code_challenge_method","S256"))
            .andExpect(status().is3xxRedirection()).andReturn();
        String location=start.getResponse().getRedirectedUrl();
        assertTrue(location.contains("/oauth/consent"),location);
        var query=org.springframework.web.util.UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        var approved=mvc.perform(post("/oauth2/authorize").with(user("alice")).with(csrf())
            .session((org.springframework.mock.web.MockHttpSession)start.getRequest().getSession(false))
            .param("client_id",app.clientId()).param("state",URLDecoder.decode(query.getFirst("state"),StandardCharsets.UTF_8)).param("scope","demo:target:read"))
            .andExpect(status().is3xxRedirection()).andReturn();
        String code=org.springframework.web.util.UriComponentsBuilder.fromUriString(approved.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("code");
        assertNotNull(code,approved.getResponse().getRedirectedUrl());
        code=URLDecoder.decode(code,StandardCharsets.UTF_8);
        mvc.perform(post("/oauth2/token").with(httpBasic(app.clientId(),app.clientSecret()))
            .param("grant_type","authorization_code").param("code",code).param("redirect_uri","http://localhost/callback").param("code_verifier","wrong"))
            .andExpect(status().isBadRequest());
        var tokens=mvc.perform(post("/oauth2/token").with(httpBasic(app.clientId(),app.clientSecret()))
            .param("grant_type","authorization_code").param("code",code).param("redirect_uri","http://localhost/callback").param("code_verifier",verifier))
            .andExpect(status().isOk()).andReturn();
        String token=json.readTree(tokens.getResponse().getContentAsString()).path("access_token").asText();
        assertFalse(token.isBlank());
        var access=new com.alisa.user.service.AccessTokenService();access.setDelegated(identities);
        assertEquals("alice",access.requireUserId("Bearer "+token));
        access.requireScope("Bearer "+token,"demo","target","read");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.requireScope("Bearer "+token,"demo","target","delete"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.requireScope("Bearer "+token,"another","target","read"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.requireFirstPartyUserId("Bearer "+token));
        mvc.perform(post("/oauth2/introspect").with(httpBasic(resource.clientId(),resource.clientSecret())).param("token",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.scope").value("demo:target:read"))
            .andExpect(jsonPath("$.userId").value("alice")).andExpect(jsonPath("$.application_id").value("demo"));
        mvc.perform(post("/oauth2/introspect").with(httpBasic(app.clientId(),app.clientSecret())).param("token",token)).andExpect(status().isForbidden());
        mvc.perform(post("/oauth2/token").with(httpBasic(resource.clientId(),resource.clientSecret())).param("grant_type","client_credentials")).andExpect(status().isForbidden());
        apps.revoke("someone-else",app.clientId());
        assertNotNull(authorizations.findByToken(token,OAuth2TokenType.ACCESS_TOKEN));
        apps.revoke("alice",app.clientId());
        assertThrows(com.alisa.security.AccessFailure.class,()->identities.resolve(token));
        mvc.perform(post("/oauth2/introspect").with(httpBasic(resource.clientId(),resource.clientSecret())).param("token",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }
    @Test void registrationAndManagementCannotEscalatePermissions() throws Exception {
        assertThrows(IllegalArgumentException.class,()->apps.create("a","demo","Bad",Set.of("https://example.org/cb"),Set.of("demo:admin:write"),false));
        assertThrows(IllegalArgumentException.class,()->apps.create("a","demo","Bad",Set.of("http://example.org/cb"),Set.of("demo:target:read"),false));
        var app=apps.create("alice","demo","App",Set.of("https://example.org/cb"),Set.of("demo:target:read"),true);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->apps.disable("bob",app.clientId()));
        mvc.perform(post("/oauth/apps/disable").with(user("alice")).param("clientId",app.clientId())).andExpect(status().isForbidden());
        apps.disable("alice",app.clientId());assertNull(apps.findByClientId(app.clientId()));
        mvc.perform(get("/oauth2/authorize").with(user("alice")).param("client_id",app.clientId())).andExpect(status().isBadRequest());
    }
    @Test void prefixedBrowserPagesKeepNavigationFormsAndRedirects() throws Exception {
        var app=apps.create("prefix-owner","demo","Prefixed App",Set.of("http://localhost/callback"),Set.of("demo:target:read"),false);
        mvc.perform(get("/auth/oauth/apps").contextPath("/auth").with(user("prefix-owner")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href='/auth/oauth/apps'")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action='/auth/oauth/apps'")));
        mvc.perform(get("/auth/oauth/consent").contextPath("/auth").with(user("alice"))
            .param("client_id",app.clientId()).param("scope","demo:target:read").param("state","test-state"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action='/auth/oauth2/authorize'")));
        mvc.perform(post("/auth/oauth/apps/disable").contextPath("/auth").with(user("prefix-owner")).with(csrf()).param("clientId",app.clientId()))
            .andExpect(status().isSeeOther()).andExpect(header().string("Location","/auth/oauth/apps"));
    }
    @Test void brandedLoginKeepsCsrfAndContextPath() throws Exception {
        mvc.perform(get("/auth/login").contextPath("/auth"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action='/auth/login'")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name='_csrf'")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("登录你的 Alisa 账号")));
        mvc.perform(post("/auth/login").contextPath("/auth").param("username","alisa").param("password","invalid"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/login").param("error","<script>bad</script>"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>bad</script>"))));
    }
}
