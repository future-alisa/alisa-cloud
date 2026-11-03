package com.alisa.user.oauth;

import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Business applications own their scope catalogs; no vendor-specific provider dependency. */
@ConfigurationProperties("alisa.oauth")
public class OAuthProperties {
    private String issuer;
    private boolean allowLoopbackRedirects;
    private Map<String, Map<String, String>> applications = new LinkedHashMap<>();
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer = value; }
    public boolean isAllowLoopbackRedirects() { return allowLoopbackRedirects; }
    public void setAllowLoopbackRedirects(boolean value) { allowLoopbackRedirects = value; }
    public Map<String, Map<String, String>> getApplications() { return applications; }
    public void setApplications(Map<String, Map<String, String>> value) { applications = value; }
    public Map<String,String> catalog(String app) {
        var result = applications.get(app);
        if (result == null || result.isEmpty()) throw new IllegalArgumentException("Unknown application");
        result.forEach((scope,label) -> {
            if (!scope.matches(java.util.regex.Pattern.quote(app) + ":[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*") || label == null || label.isBlank())
                throw new IllegalArgumentException("Invalid scope catalog entry: " + app + "/" + scope);
        });
        return Map.copyOf(result);
    }
}
