package com.alisa.user.tenant;

import java.util.*;
import javax.sql.DataSource;
import com.alisa.authorization.*;
import com.alisa.authorization.config.AuthorizationProperties;
import com.alisa.authorization.spi.AuthorizationProvider;
import com.alisa.user.oauth.*;
import com.alisa.user.service.AccessTokenService;
import com.alisa.user.mapper.UserMapper;
import com.alisa.user.provider.IdentityProviderRegistry;
import com.alisa.user.controller.AuthorizationController;
import com.alisa.config.JwtProperties;
import com.alisa.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringJUnitWebConfig(TenantFlowTest.Config.class)
@TestPropertySource(properties={"alisa.oauth.enabled=true","alisa.tenancy.enabled=true","alisa.oauth.issuer=http://localhost","alisa.oauth.allow-loopback-redirects=true",
    "alisa.oauth.applications.demo.[demo\\:target\\:read]=Read","alisa.oauth.applications.demo.[demo\\:target\\:delete]=Delete"})
class TenantFlowTest {
    @org.springframework.boot.test.context.TestConfiguration @EnableWebSecurity @EnableWebMvc @EnableTransactionManagement(proxyTargetClass=true)
    @Import({OAuthConfiguration.class,TenantConfiguration.class,TenantController.class,TenantErrors.class,TenantPages.class})
    static class Config {
        @Bean DataSource dataSource() {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            new ResourceDatabasePopulator(new ClassPathResource("sql/oauth.sql"),new ClassPathResource("sql/tenant.sql")).execute(ds);return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) {return new JdbcTemplate(ds);}
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean IdentityProviderRegistry providers() {return new IdentityProviderRegistry(List.of());}
        @Bean UserMapper users() {
            var users=mock(UserMapper.class);when(users.findUserByID(anyString())).thenAnswer(call->{var user=new com.alisa.user.entity.User();user.setId(call.getArgument(0));user.setUsername(call.getArgument(0));return user;});return users;
        }
        @Bean AuthorizationProperties authorizationProperties() {var p=new AuthorizationProperties();p.setApplications(Map.of("demo","tenant"));return p;}
        @Bean AuthorizationEngine engine(List<AuthorizationProvider> providers) {return new AuthorizationEngine(providers,Map.of("demo","tenant"));}
        @Bean AccessTokenService accessTokenService() {new JwtUtil().setJwtProperties(new JwtProperties());return new AccessTokenService();}
        @Bean AuthorizationController authorizationController(AuthorizationEngine engine,AuthorizationProperties props,AccessTokenService tokens) {return new AuthorizationController(engine,props,tokens);}
    }
    @Autowired TenantDirectory tenants;@Autowired OAuthApplications apps;@Autowired AccessTokenService tokens;
    @Autowired WebApplicationContext context;
    MockMvc mvc;ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() {mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    String userToken(String user) {var account=new com.alisa.user.entity.User();account.setId(user);account.setUsername(user);return "Bearer "+tokens.issue(account);}
    String machine(OAuthApplications.Created client,String scope) throws Exception {
        var response=mvc.perform(post("/oauth2/token").with(httpBasic(client.clientId(),client.clientSecret())).param("grant_type","client_credentials").param("scope",scope))
            .andExpect(status().isOk()).andReturn();return json.readTree(response.getResponse().getContentAsString()).path("access_token").asText();
    }
    @Test void machineAccessRequiresApprovalScopeTenantAndResourcePermission() throws Exception {
        String a=tenants.create("owner-a","A"),b=tenants.create("owner-b","B");
        var app=apps.createTenant(a,"owner-a","demo","Sync",Set.of(),Set.of("demo:target:read","demo:target:delete"),true,false);
        mvc.perform(post("/oauth2/token").with(httpBasic(app.clientId(),app.clientSecret())).param("grant_type","client_credentials")).andExpect(status().isUnauthorized());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->apps.approve(b,"owner-b",app.clientId(),Set.of("demo:target:read"),true));
        apps.approve(a,"owner-a",app.clientId(),Set.of("demo:target:read"),true);
        String token=machine(app,"demo:target:read");var identity=tokens.identity("Bearer "+token);
        assertTrue(identity.application());assertNull(identity.userId());assertEquals(a,identity.tenantId());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tokens.requireUserId("Bearer "+token));
        String body="{\"applicationId\":\"demo\",\"resourceType\":\"target\",\"resourceId\":\"42\",\"action\":\"read\"}";
        mvc.perform(post("/api/v1/authorization/check").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(jsonPath("$.data.effect").value("DENY"));
        tenants.permission(a,"owner-a",new TenantDirectory.Permission("demo","APPLICATION",app.clientId(),"target","*","read"),true);
        mvc.perform(post("/api/v1/authorization/check").header("Authorization","Bearer "+token).contentType("application/json").content(body.replace("\"read\"","\"delete\""))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/authorization/check").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(jsonPath("$.data.effect").value("PERMIT"));
        tenants.permission(a,"owner-a",new TenantDirectory.Permission("demo","APPLICATION",app.clientId(),"target","*","read"),false);
        mvc.perform(post("/api/v1/authorization/check").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(jsonPath("$.data.effect").value("DENY"));
        var resourceB=apps.tenantResource(b,"owner-b","demo","B API");
        mvc.perform(post("/oauth2/introspect").with(httpBasic(resourceB.clientId(),resourceB.clientSecret())).param("token",token)).andExpect(jsonPath("$.active").value(false));
        var resourceA=apps.tenantResource(a,"owner-a","demo","A API");
        mvc.perform(post("/oauth2/introspect").with(httpBasic(resourceA.clientId(),resourceA.clientSecret())).param("token",token))
            .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.identity_type").value("application")).andExpect(jsonPath("$.userId").doesNotExist());
        apps.rotate(a,"owner-a",app.clientId());
        mvc.perform(post("/oauth2/introspect").with(httpBasic(resourceA.clientId(),resourceA.clientSecret())).param("token",token)).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/user/tenants").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        var audits=tenants.audits(a,"owner-a",200,"");assertTrue(audits.stream().anyMatch(e->e.eventType().equals("APPLICATION_APPROVED")));
        assertTrue(audits.stream().noneMatch(e->e.details().contains(app.clientSecret()) || e.details().contains(token)));
    }
    @Test void invitationsMembershipAndOwnershipAreIsolatedAndRevocable() throws Exception {
        String a=tenants.create("alice","A"),b=tenants.create("bob","B");
        var invitation=tenants.invite(a,"alice","charlie","MEMBER");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.accept(a,"bob",invitation.id(),invitation.token()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.accept(b,"charlie",invitation.id(),invitation.token()));
        tenants.accept(a,"charlie",invitation.id(),invitation.token());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.accept(a,"charlie",invitation.id(),invitation.token()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.changeRole(a,"charlie","charlie","ADMIN"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.remove(a,"alice","alice"));
        mvc.perform(post("/api/v1/user/tenants/"+a+"/token").header("Authorization",userToken("charlie"))).andExpect(status().isOk()).andExpect(jsonPath("$.tenant_id").value(a));
        mvc.perform(post("/api/v1/user/tenants/"+b+"/token").header("Authorization",userToken("charlie"))).andExpect(status().isForbidden());
        tenants.transfer(a,"alice","charlie");assertEquals("OWNER",tenants.role(a,"charlie"));assertEquals("ADMIN",tenants.role(a,"alice"));
        tenants.remove(a,"charlie","alice");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.member(a,"alice"));
        String oldVersion=tenants.tokenVersion(a,"charlie");
        tenants.update(a,"charlie","A",false);assertFalse(tenants.active(a));
        tenants.update(a,"charlie","A renamed",true);assertTrue(tenants.active(a));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.validateTokenVersion(a,"charlie",oldVersion));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.audits(a,"bob",10,""));
    }
    @Test void browserManagementRequiresCsrfAndTenantMembership() throws Exception {
        String tenant=tenants.create("page-owner","Page tenant");
        mvc.perform(get("/api/v1/user/tenants/"+tenant+"/catalog").header("Authorization",userToken("page-owner"))).andExpect(status().isOk()).andExpect(jsonPath("$.demo['demo:target:read']").value("Read"));
        mvc.perform(get("/api/v1/user/tenants/"+tenant+"/catalog").header("Authorization",userToken("outsider"))).andExpect(status().isForbidden());
        mvc.perform(get("/oauth/tenants/"+tenant).with(user("page-owner"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Page tenant")));
        mvc.perform(get("/oauth/tenants/"+tenant).with(user("outsider"))).andExpect(status().isForbidden());
        mvc.perform(post("/oauth/tenants/"+tenant+"/settings").with(user("page-owner")).param("name","changed")).andExpect(status().isForbidden());
        var invitation=tenants.invite(tenant,"page-owner","invitee","READER");tenants.cancelInvitation(tenant,"page-owner",invitation.id());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tenants.accept(tenant,"invitee",invitation.id(),invitation.token()));
    }
    @Test void delegatedConsentCannotCrossTenantOrSurviveMembershipRemoval() throws Exception {
        String tenant=tenants.create("delegate-owner","Delegated tenant");
        var invitation=tenants.invite(tenant,"delegate-owner","delegate-user","MEMBER");tenants.accept(tenant,"delegate-user",invitation.id(),invitation.token());
        var client=apps.createTenant(tenant,"delegate-owner","demo","Reader",Set.of("http://localhost/cb"),Set.of("demo:target:read"),false,false);
        apps.approve(tenant,"delegate-owner",client.clientId(),Set.of("demo:target:read"),true);
        String challenge=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        mvc.perform(get("/oauth2/authorize").with(user("outsider")).param("client_id",client.clientId()).param("response_type","code").param("redirect_uri","http://localhost/cb").param("scope","demo:target:read").param("state","state").param("code_challenge",challenge).param("code_challenge_method","S256")).andExpect(status().isForbidden());
        var start=mvc.perform(get("/oauth2/authorize").with(user("delegate-user")).param("client_id",client.clientId()).param("response_type","code").param("redirect_uri","http://localhost/cb").param("scope","demo:target:read").param("state","state").param("code_challenge",challenge).param("code_challenge_method","S256")).andExpect(status().is3xxRedirection()).andReturn();
        var params=org.springframework.web.util.UriComponentsBuilder.fromUriString(start.getResponse().getRedirectedUrl()).build().getQueryParams();
        tenants.remove(tenant,"delegate-owner","delegate-user");
        mvc.perform(post("/oauth2/authorize").with(user("delegate-user")).with(csrf()).session((org.springframework.mock.web.MockHttpSession)start.getRequest().getSession(false))
            .param("client_id",client.clientId()).param("state",java.net.URLDecoder.decode(params.getFirst("state"),java.nio.charset.StandardCharsets.UTF_8)).param("scope","demo:target:read")).andExpect(status().isForbidden());
    }
}
