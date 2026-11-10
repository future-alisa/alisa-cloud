package com.alisa.user.oauth;

import java.net.URI;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public class OAuthApplications implements RegisteredClientRepository {
    private com.alisa.user.tenant.TenantDirectory tenants;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setTenants(com.alisa.user.tenant.TenantDirectory value) {tenants=value;}
    public record TenantClient(String clientId,String ownerId,String name,String kind,String approval,boolean enabled,Set<String> requestedScopes,Set<String> scopes,String applicationId) {}
    public record TenantInfo(String tenantId,String kind,String approval) {}
    public TenantInfo tenantInfo(String clientId) {
        if(tenants==null) return new TenantInfo("","application","APPROVED");
        var records=jdbc.query("select tenant_id,kind,approval from alisa_oauth_application where client_id=?",(r,n)->new TenantInfo(r.getString(1),r.getString(2),r.getString(3)),clientId);
        return records.isEmpty()?null:records.get(0);
    }
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.alisa.user.tenant.OrganizationDirectory> organizations;

    public void requireTenantUser(String clientId,String user) {
        var directory = organizations == null ? null : organizations.getIfAvailable();
        if (directory != null) directory.activeSessionVersion(user);
        var info=tenantInfo(clientId);
        if(info!=null && !info.tenantId().isBlank()) tenants.member(info.tenantId(),user);
    }
    public String kind(String clientId) {var info=tenantInfo(clientId);return info==null?null:info.kind();}
    private final JdbcRegisteredClientRepository clients;
    private final JdbcTemplate jdbc;
    private final OAuthProperties properties;
    public OAuthApplications(JdbcTemplate jdbc, OAuthProperties properties) {
        this.jdbc = jdbc; this.properties = properties; this.clients = new JdbcRegisteredClientRepository(jdbc);
        properties.getApplications().keySet().forEach(properties::catalog);
    }
    public record Created(String clientId, String clientSecret) {}
    public record Application(String clientId, String name, String applicationId, boolean enabled) {}
    public record Connection(String clientId, String name, String authorities) {}
    public List<Application> owned(String owner) {
        return jdbc.query("select a.client_id,c.client_name,a.application_id,a.enabled from alisa_oauth_application a join oauth2_registered_client c on c.client_id=a.client_id where a.owner_id=? and a.kind='application'",
            (r,n)->new Application(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4)),owner);
    }
    @Transactional
    public Created create(String owner, String app, String name, Set<String> redirects, Set<String> scopes, boolean publicClient) {
        var catalog = properties.catalog(app);
        if (name == null || name.isBlank() || name.length()>100 || redirects == null || redirects.isEmpty() || redirects.size()>10
            || scopes == null || scopes.isEmpty() || !catalog.keySet().containsAll(scopes)) throw new IllegalArgumentException("Invalid application or scopes");
        redirects.forEach(uri -> validateRedirect(app, publicClient, uri));
        String id=UUID.randomUUID().toString(), clientId=UUID.randomUUID().toString(), secret=publicClient?null:secret();
        var builder = RegisteredClient.withId(id).clientId(clientId).clientName(name.trim()).clientIdIssuedAt(Instant.now())
            .clientAuthenticationMethod(publicClient?ClientAuthenticationMethod.NONE:ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUris(uris->uris.addAll(redirects)).scopes(values->values.addAll(scopes))
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
            .tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .accessTokenTimeToLive(Duration.ofMinutes(15)).reuseRefreshTokens(false).refreshTokenTimeToLive(Duration.ofDays(7)).build());
        if (!publicClient) builder.clientSecret("{bcrypt}"+new BCryptPasswordEncoder().encode(secret)).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        clients.save(builder.build());
        jdbc.update("insert into alisa_oauth_application(client_id,owner_id,application_id,kind,enabled) values(?,?,?,'application',true)",clientId,owner,app);
        return new Created(clientId,secret);
    }
    /** Platform administrators create dedicated introspection credentials, never delegated API credentials. */
    @Transactional
    public Created resourceServer(String owner,String app,String name) {
        properties.catalog(app);
        if(name==null || name.isBlank() || name.length()>100) throw new IllegalArgumentException("Invalid resource server name");
        String client=UUID.randomUUID().toString(), secret=secret();
        clients.save(RegisteredClient.withId(UUID.randomUUID().toString()).clientId(client).clientName(name)
            .clientSecret("{bcrypt}"+new BCryptPasswordEncoder().encode(secret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build());
        jdbc.update("insert into alisa_oauth_application(client_id,owner_id,application_id,kind,enabled) values(?,?,?,'resource',true)",client,owner,app);
        return new Created(client,secret);
    }
    public String applicationId(String clientId) {
        var values=jdbc.queryForList("select application_id from alisa_oauth_application where client_id=? and enabled=true",String.class,clientId);
        if(values.isEmpty()) return null;
        if(tenants!=null) {
            var info=tenantInfo(clientId);
            if(info==null || !"APPROVED".equals(info.approval()) || !info.tenantId().isBlank() && !tenants.active(info.tenantId())) return null;
        }
        return values.get(0);
    }
    public boolean resource(String clientId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select count(*)>0 from alisa_oauth_application where client_id=? and kind='resource' and enabled=true",Boolean.class,clientId));
    }
    @Transactional
    public void disable(String owner,String clientId) {
        if (jdbc.update("update alisa_oauth_application set enabled=false where owner_id=? and client_id=? and kind='application'",owner,clientId)!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var client=clients.findByClientId(clientId);
        jdbc.update("delete from oauth2_authorization where registered_client_id=?",client.getId());
        jdbc.update("delete from oauth2_authorization_consent where registered_client_id=?",client.getId());
    }
    public List<Connection> connections(String user) {
        return jdbc.query("select c.client_id,c.client_name,s.authorities from oauth2_authorization_consent s join oauth2_registered_client c on c.id=s.registered_client_id where s.principal_name=?",
            (r,n)->new Connection(r.getString(1),r.getString(2),r.getString(3)),user);
    }
    @Transactional
    public void revoke(String user,String clientId) {
        var client=clients.findByClientId(clientId);
        if(client==null) return;
        jdbc.update("delete from oauth2_authorization where registered_client_id=? and principal_name=?",client.getId(),user);
        jdbc.update("delete from oauth2_authorization_consent where registered_client_id=? and principal_name=?",client.getId(),user);
        audit(clientId,user,"USER_GRANT_REVOKED");
    }
    static final String SMALLOKR_NATIVE_CALLBACK = "com.sunyubo.smallokr:/oauth2/callback";

    private void validateRedirect(String app, boolean publicClient, String value) {
        if ("smallokr".equals(app) && publicClient && SMALLOKR_NATIVE_CALLBACK.equals(value)) {
            return;
        }
        URI uri=URI.create(value);
        boolean loopback=Set.of("localhost","127.0.0.1","[::1]").contains(Objects.toString(uri.getHost(),""));
        if(uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getFragment()!=null || value.contains("*")
            || !("https".equals(uri.getScheme()) || properties.isAllowLoopbackRedirects() && loopback && "http".equals(uri.getScheme())))
            throw new IllegalArgumentException("Redirect URI requires exact HTTPS address");
    }
    private String secret() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    @Override public void save(RegisteredClient client) { clients.save(client); }
    @Override public RegisteredClient findById(String id) { return clients.findById(id); }
    @Override public RegisteredClient findByClientId(String id) { return applicationId(id)==null?null:clients.findByClientId(id); }

    @Transactional public Created createTenant(String tenant,String owner,String app,String name,Set<String> redirects,Set<String> scopes,boolean machine,boolean publicClient) {
        requireTenants();lockTenant(tenant);tenants.member(tenant,owner);
        if(scopes==null || scopes.isEmpty() || !properties.catalog(app).keySet().containsAll(scopes) || String.join(" ",scopes).length()>800 || name==null || name.isBlank() || name.length()>100) throw new IllegalArgumentException("Invalid application");
        Created created;
        if(!machine) created=create(owner,app,name,redirects,scopes,publicClient);
        else {
            if(publicClient) throw new IllegalArgumentException("Machine applications require confidential credentials");
            String id=UUID.randomUUID().toString(),client=UUID.randomUUID().toString(),secret=secret();
            clients.save(RegisteredClient.withId(id).clientId(client).clientName(name).clientSecret("{bcrypt}"+new BCryptPasswordEncoder().encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(s->s.addAll(scopes)).tokenSettings(TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).accessTokenTimeToLive(Duration.ofMinutes(15)).build()).build());
            jdbc.update("insert into alisa_oauth_application(client_id,owner_id,application_id,kind,enabled) values(?,?,?,'machine',true)",client,owner,app);created=new Created(client,secret);
        }
        jdbc.update("update alisa_oauth_application set tenant_id=?,approval='PENDING',requested_scopes=? where client_id=?",tenant,String.join(" ",scopes),created.clientId());
        tenants.audit(tenant,owner,"APPLICATION_REQUESTED",created.clientId(),"{}");return created;
    }
    public List<TenantClient> tenantClients(String tenant,String actor) {
        requireTenants();tenants.member(tenant,actor);boolean admin=Set.of("OWNER","ADMIN").contains(tenants.role(tenant,actor));
        return jdbc.query("select a.client_id,a.owner_id,c.client_name,a.kind,a.approval,a.enabled,a.requested_scopes,c.id,a.application_id from alisa_oauth_application a join oauth2_registered_client c on c.client_id=a.client_id where a.tenant_id=? and (?=true or a.owner_id=?)",
            (r,n)->new TenantClient(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getBoolean(6),scopeSet(r.getString(7)),clients.findById(r.getString(8)).getScopes(),r.getString(9)),tenant,admin,actor);
    }
    @Transactional public void approve(String tenant,String actor,String clientId,Set<String> scopes,boolean approve) {
        requireTenants();lockTenant(tenant);tenants.admin(tenant,actor);var info=tenantInfo(clientId);
        if(info==null || !tenant.equals(info.tenantId()) || !Set.of("machine","application").contains(info.kind())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var requested=scopeSet(jdbc.queryForObject("select requested_scopes from alisa_oauth_application where client_id=?",String.class,clientId));
        var client=clients.findByClientId(clientId);
        if(approve && (scopes==null || scopes.isEmpty() || !requested.containsAll(scopes) || !properties.catalog(jdbc.queryForObject("select application_id from alisa_oauth_application where client_id=?",String.class,clientId)).keySet().containsAll(scopes))) throw new IllegalArgumentException("Invalid approval scopes");
        // Any approval change invalidates old grants, including authorization codes and refresh tokens.
        clearTokens(client.getId());
        clients.save(RegisteredClient.from(client).scopes(s->{s.clear();if(approve)s.addAll(scopes);}).build());
        jdbc.update("update alisa_oauth_application set approval=? where client_id=?",approve?"APPROVED":"REJECTED",clientId);
        tenants.audit(tenant,actor,approve?"APPLICATION_APPROVED":"APPLICATION_REJECTED",clientId,"{}");
    }
    @Transactional public void disableTenantClient(String tenant,String actor,String clientId) {
        requireTenants();lockTenant(tenant);tenants.member(tenant,actor);var info=tenantInfo(clientId);
        if(info==null || !tenant.equals(info.tenantId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var owner=jdbc.queryForObject("select owner_id from alisa_oauth_application where client_id=?",String.class,clientId);
        if(!actor.equals(owner)) tenants.admin(tenant,actor);
        jdbc.update("update alisa_oauth_application set enabled=false where client_id=?",clientId);clearTokens(clients.findByClientId(clientId).getId());
        tenants.audit(tenant,actor,"APPLICATION_DISABLED",clientId,"{}");
    }
    @Transactional public Created rotate(String tenant,String actor,String clientId) {
        requireTenants();lockTenant(tenant);tenants.admin(tenant,actor);var info=tenantInfo(clientId);
        if(info==null || !tenant.equals(info.tenantId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var client=clients.findByClientId(clientId);
        if(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) throw new IllegalArgumentException("Public client has no secret");
        String secret=secret();clients.save(RegisteredClient.from(client).clientSecret("{bcrypt}"+new BCryptPasswordEncoder().encode(secret)).build());clearTokens(client.getId());
        tenants.audit(tenant,actor,"CLIENT_SECRET_ROTATED",clientId,"{}");return new Created(clientId,secret);
    }
    @Transactional public Created tenantResource(String tenant,String actor,String app,String name) {
        requireTenants();lockTenant(tenant);tenants.admin(tenant,actor);var created=resourceServer(actor,app,name);
        jdbc.update("update alisa_oauth_application set tenant_id=? where client_id=?",tenant,created.clientId());
        tenants.audit(tenant,actor,"RESOURCE_CLIENT_CREATED",created.clientId(),"{}");return created;
    }
    public void audit(String clientId,String actor,String event) {var info=tenantInfo(clientId);if(tenants!=null && info!=null) tenants.audit(info.tenantId(),actor,event,clientId,"{}");}
    private void requireTenants() {if(tenants==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private void lockTenant(String tenant) {if(jdbc.queryForList("select id from alisa_tenant where id=? for update",String.class,tenant).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private Set<String> scopeSet(String scope) {return scope==null || scope.isBlank()?Set.of():new LinkedHashSet<>(Arrays.asList(scope.split(" +")));}
    private void clearTokens(String id) {jdbc.update("delete from oauth2_authorization where registered_client_id=?",id);jdbc.update("delete from oauth2_authorization_consent where registered_client_id=?",id);}
}
