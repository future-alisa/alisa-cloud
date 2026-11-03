package com.alisa.security;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenVerifierTest {
    HttpServer server;
    TokenVerifier verifier;
    AtomicReference<Map<String,Object>> claims=new AtomicReference<>();
    AtomicReference<String> effect=new AtomicReference<>("PERMIT");
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/introspect",exchange->{
            assertEquals("Basic YXBpOnNlY3JldA==",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] data=new ObjectMapper().writeValueAsBytes(claims.get());exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });
        server.createContext("/check",exchange->{
            assertEquals("Bearer opaque",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] data=("{\"data\":{\"effect\":\""+effect.get()+"\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });server.start();
        var config=new ResourceSecurityProperties.ResourceServer();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        config.setIntrospectionUri(base+"/introspect");config.setAuthorizationUri(base+"/check");config.setIssuer("https://issuer.example");
        config.setClientId("api");config.setClientSecret("secret");config.setAllowInsecureLoopback(true);
        var props=new ResourceSecurityProperties();props.setResources(Map.of("demo",config));verifier=new TokenVerifier(props);
        claims.set(new HashMap<>(Map.of("active",true,"application_id","demo","aud",List.of("demo"),"iss","https://issuer.example","userId","alice","client_id","third-party","exp",Instant.now().plusSeconds(300).getEpochSecond(),"scope","demo:target:read")));
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void bothScopeAndUserPermissionAreRequired() {
        var user=verifier.verify("Bearer opaque","demo");assertEquals("alice",user.userId());
        verifier.require(user,"Bearer opaque","demo","target","42","read");
        assertEquals(403,assertThrows(AccessFailure.class,()->verifier.require(user,"Bearer opaque","demo","target","42","delete")).status());
        effect.set("DENY");assertEquals(403,assertThrows(AccessFailure.class,()->verifier.require(user,"Bearer opaque","demo","target","42","read")).status());
    }
    @Test void wrongAudienceIssuerExpiredAndInactiveTokensAreRejected() {
        for(String key:List.of("aud","iss","application_id")) {
            Object original=claims.get().put(key,"wrong");
            assertEquals(401,assertThrows(AccessFailure.class,()->verifier.verify("Bearer opaque","demo")).status());claims.get().put(key,original);
        }
        claims.get().put("exp",0);assertThrows(AccessFailure.class,()->verifier.verify("Bearer opaque","demo"));
        claims.set(Map.of("active",false));assertThrows(AccessFailure.class,()->verifier.verify("Bearer opaque","demo"));
    }
    @Test void applicationTokensHaveNoUserAndMustCarryATenant() {
        claims.get().put("identity_type","application");claims.get().put("tenant_id","tenant-a");claims.get().remove("userId");
        var identity=verifier.verify("Bearer opaque","demo");assertTrue(identity.application());assertNull(identity.userId());assertEquals("tenant-a",identity.tenantId());
        verifier.require(identity,"Bearer opaque","demo","target","*","read");
        claims.get().remove("tenant_id");assertThrows(AccessFailure.class,()->verifier.verify("Bearer opaque","demo"));
    }
    @Test void tenantModeNeverFallsBackToLegacyOrTrustsATenantHeader() {
        var mock=org.mockito.Mockito.mock(TokenVerifier.class);var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("X-Tenant-Id","forged");
        org.mockito.Mockito.when(mock.verify(null,"demo")).thenReturn(RequestIdentity.firstParty("alice","Alice"));
        assertThrows(AccessFailure.class,()->new ResourceAccess(mock,"demo",true).authenticate(request));
        org.mockito.Mockito.when(mock.verify(null,"demo")).thenReturn(new RequestIdentity("alice","Alice",false,null,null,Set.of(),"tenant-a",false));
        assertEquals("tenant-a",new ResourceAccess(mock,"demo",true).authenticate(request).tenantId());
        assertThrows(AccessFailure.class,()->new ResourceAccess(mock,"demo",false).authenticate(request));
    }
}
