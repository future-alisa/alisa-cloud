package com.alisa.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("alisa.security")
public class ResourceSecurityProperties {
    private boolean requireTenant;
    private String tenantTable;
    public String getTenantTable() {return tenantTable;}
    public void setTenantTable(String value) {tenantTable=value;}
    public boolean isRequireTenant() {return requireTenant;}
    public void setRequireTenant(boolean value) {requireTenant=value;}
    private String applicationId;
    private Map<String, ResourceServer> resources = new LinkedHashMap<>();
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String value) { applicationId = value; }
    public Map<String, ResourceServer> getResources() { return resources; }
    public void setResources(Map<String, ResourceServer> value) { resources = value; }

    public static class ResourceServer {
        private String issuer;
        private String introspectionUri;
        private String authorizationUri;
        private String clientId;
        private String clientSecret;
        private boolean allowInsecureLoopback;
        public String getIssuer() { return issuer; }
        public void setIssuer(String value) { issuer = value; }
        public String getIntrospectionUri() { return introspectionUri; }
        public void setIntrospectionUri(String value) { introspectionUri = value; }
        public String getAuthorizationUri() { return authorizationUri; }
        public void setAuthorizationUri(String value) { authorizationUri = value; }
        public String getClientId() { return clientId; }
        public void setClientId(String value) { clientId = value; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String value) { clientSecret = value; }
        public boolean isAllowInsecureLoopback() { return allowInsecureLoopback; }
        public void setAllowInsecureLoopback(boolean value) { allowInsecureLoopback = value; }
    }
}
