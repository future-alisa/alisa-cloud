package com.alisa.authorization.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("alisa.authorization")
public class AuthorizationProperties {
    private Map<String, String> applications = new LinkedHashMap<>();
    private Set<String> administrators = new LinkedHashSet<>();
    public Map<String, String> getApplications() { return applications; }
    public void setApplications(Map<String, String> value) { applications = value; }
    public Set<String> getAdministrators() { return administrators; }
    public void setAdministrators(Set<String> value) { administrators = value; }
}
