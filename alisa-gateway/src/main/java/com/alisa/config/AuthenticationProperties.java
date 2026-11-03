package com.alisa.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("alisa.gateway.auth")
public class AuthenticationProperties {
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/oauth2/**", "/oauth/**", "/login", "/logout", "/.well-known/**", "/api/v1/authorization/check",
            "/api/v1/user/send", "/api/v1/user/signin", "/api/v1/user/signin/*",
            "/api/v1/user/providers", "/api/v1/user/signup",
            "/api/v1/user/change-password", "/api/v1/user/change-password-email", "/api/v1/user/health"));
    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> value) { publicPaths = value; }
}
