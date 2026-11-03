package com.alisa.security;

import java.util.Set;
public record RequestIdentity(String userId, String username, boolean delegated,
        String clientId, String applicationId, Set<String> scopes, String tenantId, boolean application,String tenantVersion) {
    public RequestIdentity(String userId,String username,boolean delegated,String clientId,String applicationId,Set<String> scopes,String tenantId,boolean application) {
        this(userId,username,delegated,clientId,applicationId,scopes,tenantId,application,"");
    }
    public RequestIdentity(String userId,String username,boolean delegated,String clientId,String applicationId,Set<String> scopes) {
        this(userId,username,delegated,clientId,applicationId,scopes,"",false);
    }
    public RequestIdentity {
        tenantId=tenantId==null?"":tenantId;
        tenantVersion=tenantVersion==null?"":tenantVersion;
        if(application) {
            if(userId!=null || clientId==null || clientId.isBlank() || tenantId.isBlank() || !delegated) throw new IllegalArgumentException("Invalid application identity");
        } else if (userId == null || userId.isBlank()) throw new IllegalArgumentException("Missing user id");
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
    public static RequestIdentity firstParty(String userId, String username) {
        return new RequestIdentity(userId, username, false, null, null, Set.of());
    }
}
