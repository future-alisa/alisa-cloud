package com.alisa.filters;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.alisa.config.AuthenticationProperties;
import com.alisa.config.JwtProperties;
import com.alisa.model.UserContext;
import com.alisa.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFilterTest {
    AuthenticationFilter filter = new AuthenticationFilter(new AuthenticationProperties());
    @Test void opaqueTokensAreForwardedOnlyToVerifyingBackendsWithoutIdentityHeaders() {
        var unready=MockServerWebExchange.from(MockServerHttpRequest.get("/targets").header("Authorization","Bearer opaque"));
        filter.filter(unready,ignored->{fail("unready backend");return Mono.empty();}).block();
        assertEquals(HttpStatus.UNAUTHORIZED,unready.getResponse().getStatusCode());
        var ready=MockServerWebExchange.from(MockServerHttpRequest.get("/targets").header("Authorization","Bearer opaque").header("X-User-Header","forged"));
        ready.getAttributes().put(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
            org.springframework.cloud.gateway.route.Route.async().id("verified").uri("http://localhost").asyncPredicate(ignored->Mono.just(true)).metadata("delegated-ready",true).build());
        var forwarded=new java.util.concurrent.atomic.AtomicBoolean();
        filter.filter(ready,request->{forwarded.set(true);assertNull(request.getRequest().getHeaders().getFirst("X-User-Header"));return Mono.empty();}).block();
        assertTrue(forwarded.get());
    }
    @Test void anonymousCallsCannotReadProtectedRoutes() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile"));
        filter.filter(exchange, ignored -> { fail("must not forward"); return Mono.empty(); }).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
    @Test void publicResetRouteRemovesSpoofedIdentityHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/user/change-password-email")
                .header("X-User-Header", "forged").header("X-Authenticated", "true"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); }).block();
        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Header"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Authenticated"));
    }
    @Test void sharedJwtContractProducesTheSameDownstreamIdentity() {
        new JwtUtil().setJwtProperties(new JwtProperties());
        String token = JwtUtil.GenerateToken("用户\"alice", Map.of("userId", "existing-id"));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/user/profile")
                .header("Authorization", "Bearer " + token).header("X-User-Header", "forged"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, request -> { forwarded.set(request); return Mono.empty(); }).block();
        String header = forwarded.get().getRequest().getHeaders().getFirst("X-User-Header");
        UserContext user = UserContext.fromBase64Json(header);
        assertEquals("existing-id", user.getUserId());
        assertEquals("用户\"alice", user.getUsername());
    }
    @Test void defaultGatewayConfigurationContainsOnlyPlatformRoutes() throws Exception {
        var properties = new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yml")).get(0);
        assertEquals("user-service", properties.getProperty("spring.cloud.gateway.routes[0].id"));
        assertNull(properties.getProperty("spring.cloud.gateway.routes[1].id"));
    }
    @Test void genericProviderLoginIsPublicButResourceAuthorizationStillRequiresAToken() {
        var login = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/user/signin/company-idp"));
        java.util.concurrent.atomic.AtomicBoolean forwarded = new java.util.concurrent.atomic.AtomicBoolean();
        filter.filter(login, request -> { forwarded.set(true); return Mono.empty(); }).block();
        assertTrue(forwarded.get());
        var check = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/authorization/applications/app/roles"));
        filter.filter(check, request -> { fail("must not forward anonymously"); return Mono.empty(); }).block();
        assertEquals(HttpStatus.UNAUTHORIZED, check.getResponse().getStatusCode());
    }
}
