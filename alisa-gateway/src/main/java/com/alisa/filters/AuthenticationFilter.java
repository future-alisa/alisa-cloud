package com.alisa.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ServerWebExchange;

import com.alisa.model.UserContext;
import com.alisa.util.JwtUtil;
import com.alisa.config.AuthenticationProperties;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

@CrossOrigin
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    private final AuthenticationProperties properties;
    public AuthenticationFilter(AuthenticationProperties properties) { this.properties = properties; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Identity headers are produced by this gateway, never accepted from callers.
        var sanitized = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-User-Header");
            headers.remove("X-Authenticated");
        }).build();
        exchange = exchange.mutate().request(sanitized).build();
        var request = exchange.getRequest();

        // 哪些路径不需要经过验证
        if (request.getMethod() == HttpMethod.OPTIONS || isExclude(request.getPath().toString())) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return setUnauthorized(exchange);
        }

        if(token.indexOf('.')<0) {
            var route=exchange.<org.springframework.cloud.gateway.route.Route>getAttribute(org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            // Only backends that independently validate bearer tokens may receive opaque credentials.
            if(route!=null && Boolean.TRUE.equals(route.getMetadata().get("delegated-ready"))) return chain.filter(exchange);
            return setUnauthorized(exchange);
        }

        return processTokenAndForward(token, exchange, chain);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private boolean isExclude(String requestPath) {
        AntPathMatcher antPathMatcher = new AntPathMatcher();

        for (String string : properties.getPublicPaths()) {
            if (antPathMatcher.match(string, requestPath)) {
                return true;
            }
        }

        return false;
    }

    private Mono<Void> setUnauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private Mono<Void> processTokenAndForward(String token, ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            if (JwtUtil.isTokenExpired(token)) {
                return setUnauthorized(exchange);
            }

            Claims claims = JwtUtil.extractAllClaims(token);
            String username = claims.getSubject();
            Object userId = claims.get("userId");

            if (username != null && JwtUtil.validateToken(token, username)) {
                // 创建 UserContext 对象并赋值
                UserContext userContext = new UserContext();
                userContext.setUserId(userId == null ? "" : String.valueOf(userId));
                userContext.setUsername(username);

                String base64User = userContext.toBase64Json();

                // 将结果塞入请求头传递
                var newRequest = exchange.getRequest()
                        .mutate()
                        .header("X-User-Header", base64User)
                        .header("X-Authenticated", "true")
                        .build();

                return chain.filter(exchange.mutate().request(newRequest).build());
            }
        } catch (Exception e) {
            // Never log bearer credentials or JWT parse details.
        }

        return setUnauthorized(exchange);
    }

    private String extractToken(org.springframework.http.server.reactive.ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader)
                && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String queryToken = request.getQueryParams().getFirst("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }

        return null;
    }
}
