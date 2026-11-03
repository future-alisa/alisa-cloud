package com.alisa.user.oauth;

import com.alisa.identity.*;
import com.alisa.user.provider.IdentityProviderRegistry;
import com.alisa.security.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.oauth2.server.authorization.web.OAuth2ClientAuthenticationFilter;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class OAuthConfiguration {
    // Adding the protocol library must not enable Basic auth or CSRF on existing bearer APIs.
    @Bean @Order(10)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/**", "/actuator/health", "/error")
            .csrf(c->c.disable()).requestCache(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.anyRequest().permitAll()).build();
    }
    @Bean
    AuthenticationProvider accountAuthentication(IdentityProviderRegistry providers) {
        return new AuthenticationProvider() {
            public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
            public Authentication authenticate(Authentication input) {
                try {
                    var identity=providers.authenticate("local",new IdentityCredentials("password",Map.of("username",input.getName(),"password",input.getCredentials().toString())));
                    var principal=User.withUsername(identity.subject()).password("").authorities("USER").build();
                    return UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
                } catch (AuthenticationRejectedException exception) { throw new BadCredentialsException("Invalid credentials"); }
            }
        };
    }
    @Bean @Order(99)
    SecurityFilterChain denyOther(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(c->c.anyRequest().denyAll()).build();
    }

    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(prefix="alisa.oauth",name="enabled",havingValue="true")
    @EnableConfigurationProperties(OAuthProperties.class)
    public static class Enabled {
        @Bean OAuthApplications oauthApplications(JdbcTemplate jdbc, OAuthProperties props, com.alisa.authorization.AuthorizationEngine requiredAuthorizationEngine) { return new OAuthApplications(jdbc,props); }
        @Bean OAuth2AuthorizationService oauthAuthorizations(JdbcTemplate jdbc,OAuthApplications clients) { return new JdbcOAuth2AuthorizationService(jdbc,clients); }
        @Bean OAuth2AuthorizationConsentService oauthConsents(JdbcTemplate jdbc,OAuthApplications clients) {
            var delegate=new JdbcOAuth2AuthorizationConsentService(jdbc,clients);
            return new OAuth2AuthorizationConsentService() {
                public void save(OAuth2AuthorizationConsent consent) {delegate.save(consent);clients.audit(clients.findById(consent.getRegisteredClientId()).getClientId(),consent.getPrincipalName(),"USER_CONSENT_SAVED");}
                public void remove(OAuth2AuthorizationConsent consent) {delegate.remove(consent);clients.audit(clients.findById(consent.getRegisteredClientId()).getClientId(),consent.getPrincipalName(),"USER_CONSENT_REVOKED");}
                public OAuth2AuthorizationConsent findById(String client,String user) {return delegate.findById(client,user);}
            };
        }
        @Bean AuthorizationServerSettings authorizationServerSettings(OAuthProperties props) {
            var uri=java.net.URI.create(props.getIssuer());
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null ||
                !("https".equals(uri.getScheme()) || props.isAllowLoopbackRedirects() && "http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1").contains(uri.getHost())))
                throw new IllegalArgumentException("OAuth issuer requires HTTPS");
            return AuthorizationServerSettings.builder().issuer(props.getIssuer()).build();
        }
        @Bean OAuth2TokenCustomizer<OAuth2TokenClaimsContext> delegatedClaims(OAuthApplications clients) {
            return context->{
                String app=clients.applicationId(context.getRegisteredClient().getClientId());
                if(app==null || clients.resource(context.getRegisteredClient().getClientId())) throw new IllegalStateException("Client cannot issue delegated tokens");
                var info=clients.tenantInfo(context.getRegisteredClient().getClientId());
                boolean machine="machine".equals(clients.kind(context.getRegisteredClient().getClientId()));
                if(machine && !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) throw new IllegalStateException("Invalid machine grant");
                if(!machine) {
                    clients.requireTenantUser(context.getRegisteredClient().getClientId(),context.getPrincipal().getName());
                    context.getClaims().claim("userId",context.getPrincipal().getName());
                }
                context.getClaims().claim("identity_type",machine?"application":"user").claim("tenant_id",info.tenantId())
                    .claim("application_id",app).audience(new ArrayList<>(List.of(app)));
                clients.audit(context.getRegisteredClient().getClientId(),machine?context.getRegisteredClient().getClientId():context.getPrincipal().getName(),"TOKEN_REQUEST_AUTHORIZED");
            };
        }
        @Bean DelegatedIdentityResolver delegatedIdentityResolver(OAuth2AuthorizationService authorizations,OAuthApplications clients) {
            return token->{
                var authorization=authorizations.findByToken(token,OAuth2TokenType.ACCESS_TOKEN);
                if(authorization==null || authorization.getAccessToken()==null || !authorization.getAccessToken().isActive()) throw new AccessFailure(401,"Inactive token");
                var client=clients.findById(authorization.getRegisteredClientId());
                String app=clients.applicationId(client.getClientId());
                if(app==null || clients.resource(client.getClientId())) throw new AccessFailure(401,"Inactive application");
                var info=clients.tenantInfo(client.getClientId());boolean machine="machine".equals(clients.kind(client.getClientId()));
                if(!machine) {
                    try {clients.requireTenantUser(client.getClientId(),authorization.getPrincipalName());}
                    catch(org.springframework.web.server.ResponseStatusException e) {throw new AccessFailure(401,"Inactive tenant membership");}
                }
                return new RequestIdentity(machine?null:authorization.getPrincipalName(),authorization.getPrincipalName(),true,client.getClientId(),app,authorization.getAuthorizedScopes(),info.tenantId(),machine);
            };
        }
        @Bean @Order(1)
        SecurityFilterChain protocol(HttpSecurity http, OAuthApplications clients, OAuth2AuthorizationService authorizations) throws Exception {
            OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
            http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).authorizationEndpoint(c->c.consentPage("/oauth/consent"));
            http.exceptionHandling(c->c.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")));
            http.addFilterBefore(new OncePerRequestFilter() {
                protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)
                        throws java.io.IOException,jakarta.servlet.ServletException {
                    String route=new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request);
                    var principal=SecurityContextHolder.getContext().getAuthentication();
                    if(route.equals("/oauth2/authorize") && principal!=null && principal.isAuthenticated() && !(principal instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                        String clientId=request.getParameter("client_id");
                        try {if(clientId!=null) clients.requireTenantUser(clientId,principal.getName());}
                        catch(org.springframework.web.server.ResponseStatusException e) {response.sendError(403,"Tenant membership required");return;}
                    }
                    if(new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request).equals("/oauth2/authorize") && ("GET".equals(request.getMethod()) || request.getParameter("response_type")!=null) &&
                        (!"S256".equals(request.getParameter("code_challenge_method")) || request.getParameter("code_challenge")==null)) {
                        response.sendError(400,"PKCE S256 required"); return;
                    }
                    chain.doFilter(request,response);
                }
            },org.springframework.security.web.authentication.logout.LogoutFilter.class);
            // Introspection is private to resource servers of the same application.
            http.addFilterAfter(new OncePerRequestFilter() {
                protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)
                        throws java.io.IOException,jakarta.servlet.ServletException {
                    String path=new org.springframework.web.util.UrlPathHelper().getPathWithinApplication(request);
                    var authentication=SecurityContextHolder.getContext().getAuthentication();
                    if(authentication instanceof OAuth2ClientAuthenticationToken client && client.isAuthenticated()) {
                        String id=client.getRegisteredClient().getClientId();
                        if(path.equals("/oauth2/token") && "refresh_token".equals(request.getParameter("grant_type"))) {
                            var existing=authorizations.findByToken(request.getParameter("refresh_token"),OAuth2TokenType.REFRESH_TOKEN);
                            if(existing!=null) try {clients.requireTenantUser(id,existing.getPrincipalName());}
                            catch(org.springframework.web.server.ResponseStatusException e) {response.sendError(403,"Tenant membership required");return;}
                        }
                        if((path.equals("/oauth2/token") && clients.resource(id)) || (path.equals("/oauth2/introspect") && !clients.resource(id))) {
                            response.setStatus(403); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"unauthorized_client\"}"); return;
                        }
                        if(path.equals("/oauth2/introspect") && request.getParameter("token")!=null) {
                            var token=authorizations.findByToken(request.getParameter("token"),OAuth2TokenType.ACCESS_TOKEN);
                            var origin=token==null?null:clients.findById(token.getRegisteredClientId());
                            var receiver=clients.tenantInfo(id);var sender=origin==null?null:clients.tenantInfo(origin.getClientId());
                            boolean tenantMismatch=receiver!=null && !receiver.tenantId().isBlank() && (sender==null || !receiver.tenantId().equals(sender.tenantId()));
                            boolean missingMember=false;
                            if(origin!=null && !"machine".equals(clients.kind(origin.getClientId()))) try {clients.requireTenantUser(origin.getClientId(),token.getPrincipalName());}
                            catch(org.springframework.web.server.ResponseStatusException e) {missingMember=true;}
                            if(origin==null || tenantMismatch || missingMember || !Objects.equals(clients.applicationId(id),clients.applicationId(origin.getClientId()))) {
                                response.setContentType("application/json"); response.getWriter().write("{\"active\":false}"); return;
                            }
                        }
                    }
                    chain.doFilter(request,response);
                }
            },org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
            return http.build();
        }
        @Bean @Order(2)
        SecurityFilterChain browser(HttpSecurity http,AuthenticationProvider accountAuthentication) throws Exception {
            return http.securityMatcher("/login","/logout","/oauth/**").authenticationProvider(accountAuthentication)
                .authorizeHttpRequests(c->c.requestMatchers("/login").permitAll().anyRequest().authenticated())
                .formLogin(c->c.defaultSuccessUrl("/oauth/apps",false)).logout(c->c.logoutSuccessUrl("/login")).build();
        }
    }
}
