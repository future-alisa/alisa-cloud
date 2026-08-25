package com.alisa.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ServerWebExchange;

import com.alisa.model.UserContext;
import com.alisa.util.JwtUtil;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

@CrossOrigin
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();

        // 哪些路径不需要经过验证
        if (isExclude(request.getPath().toString())) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return setUnauthorized(exchange);
        }

        return processTokenAndForward(token, exchange, chain);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private boolean isExclude(String requestPath) {
        final String[] excludePaths = {
                "/api/v1/user/send",
                "/api/v1/user/signin",
                "/api/v1/user/signup",
                "/api/v1/user/change-password",
                "/api/v1/user/health",
        };
        AntPathMatcher antPathMatcher = new AntPathMatcher();

        for (String string : excludePaths) {
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
            e.printStackTrace();
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
