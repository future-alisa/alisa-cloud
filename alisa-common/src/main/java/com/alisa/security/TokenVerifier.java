package com.alisa.security;

import com.alisa.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Legacy first-party JWTs plus RFC 7662 opaque delegated tokens. No token-result cache. */
public class TokenVerifier {
    private final ResourceSecurityProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    public TokenVerifier(ResourceSecurityProperties properties) { this.properties = properties; }

    public RequestIdentity verify(String authorization, String applicationId) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 16384)
            throw new AccessFailure(401, "Missing bearer token");
        String token = authorization.substring(7);
        if(token.isBlank()) throw new AccessFailure(401,"Empty token");
        if (token.chars().filter(ch -> ch == '.').count() == 2) {
            try {
                var claims = JwtUtil.extractAllClaims(token);
                String id = claims.get("userId", String.class);
                if (claims.getSubject() == null || claims.getSubject().isBlank()) throw new IllegalArgumentException();
                return new RequestIdentity(id,claims.getSubject(),false,null,null,Set.of(),claims.get("tenantId",String.class),false,claims.get("tenantVersion",String.class));
            } catch (RuntimeException exception) { throw new AccessFailure(401, "Invalid token"); }
        }
        var config = properties.getResources().get(applicationId);
        if (config == null) throw new AccessFailure(401, "Delegated access is not configured");
        Map<String, Object> claims = postForm(config.getIntrospectionUri(), config,
                "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8), true);
        if (!Boolean.TRUE.equals(claims.get("active"))) throw new AccessFailure(401, "Inactive token");
        if (config.getIssuer()==null || config.getIssuer().isBlank()) throw new AccessFailure(503,"Missing token issuer configuration");
        if (!applicationId.equals(claims.get("application_id")) || !Objects.equals(config.getIssuer(), claims.get("iss")))
            throw new AccessFailure(401, "Wrong token audience or issuer");
        Object audience = claims.get("aud");
        if (!(audience instanceof Collection<?> values && values.contains(applicationId)) && !applicationId.equals(audience))
            throw new AccessFailure(401, "Wrong token audience");
        if (!(claims.get("exp") instanceof Number expiry) || expiry.longValue() <= Instant.now().getEpochSecond())
            throw new AccessFailure(401, "Expired token");
        String id = claims.get("userId") instanceof String value ? value : null;
        String client = claims.get("client_id") instanceof String value ? value : null;
        boolean application="application".equals(claims.get("identity_type"));
        if ((!application && (id == null || id.isBlank())) || client == null || client.isBlank())
            throw new AccessFailure(401, "Missing delegated identity");
        String scope = Objects.toString(claims.get("scope"), "");
        Set<String> scopes = scope.isBlank() ? Set.of() : Set.copyOf(Arrays.asList(scope.split(" +")));
        String tenant=Objects.toString(claims.get("tenant_id"),"");
        if(application && (tenant.isBlank() || id!=null)) throw new AccessFailure(401,"Invalid application identity");
        return new RequestIdentity(id, Objects.toString(claims.get("username"), application?client:id), true, client, applicationId, scopes,tenant,application);
    }

    public void require(RequestIdentity identity, String authorization, String applicationId, String type, String id, String action) {
        if (!identity.delegated() && identity.tenantId().isBlank()) return;
        if (identity.delegated() && (!applicationId.equals(identity.applicationId()) || !identity.scopes().contains(applicationId + ":" + type + ":" + action)))
            throw new AccessFailure(403, "Insufficient delegated scope");
        var config = properties.getResources().get(applicationId);
        if (config == null) throw new AccessFailure(503, "Authorization service unavailable");
        try {
            URI uri = endpoint(config.getAuthorizationUri(), config);
            String body = json.writeValueAsString(Map.of("applicationId", applicationId, "resourceType", type, "resourceId", id, "action", action));
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("Authorization", authorization).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) throw new AccessFailure(401, "Inactive token");
            if (response.statusCode() == 403) throw new AccessFailure(403, "Tenant permission denied");
            if (response.statusCode() != 200) throw new AccessFailure(503, "Authorization service unavailable");
            var tree = json.readTree(response.body());
            if (!"PERMIT".equals(tree.path("data").path("effect").asText()))
                throw new AccessFailure(403, "User permission denied");
        } catch (AccessFailure exception) { throw exception; }
        catch (Exception exception) { if(exception instanceof InterruptedException) Thread.currentThread().interrupt(); throw new AccessFailure(503, "Authorization service unavailable"); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String url, ResourceSecurityProperties.ResourceServer config, String body, boolean basic) {
        try {
            String credentials = Base64.getEncoder().encodeToString((config.getClientId() + ":" + config.getClientSecret()).getBytes(StandardCharsets.UTF_8));
            var response = http.send(HttpRequest.newBuilder(endpoint(url, config)).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Basic " + credentials).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AccessFailure(503, "Token verification unavailable");
            return json.readValue(response.body(), Map.class);
        } catch (AccessFailure exception) { throw exception; }
        catch (Exception exception) { if(exception instanceof InterruptedException) Thread.currentThread().interrupt(); throw new AccessFailure(503, "Token verification unavailable"); }
    }

    private URI endpoint(String value, ResourceSecurityProperties.ResourceServer config) {
        if (value == null) throw new AccessFailure(503, "Missing security endpoint");
        URI uri = URI.create(value);
        boolean loopback = Set.of("127.0.0.1", "localhost", "[::1]").contains(Objects.toString(uri.getHost(), ""));
        if (uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || (config.isAllowInsecureLoopback() && loopback && "http".equals(uri.getScheme()))))
            throw new AccessFailure(503, "Security endpoints require HTTPS");
        return uri;
    }
}
