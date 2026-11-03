package com.alisa.security;

import com.alisa.model.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;

public class ResourceAccess {
    public static final String ATTRIBUTE = ResourceAccess.class.getName() + ".identity";
    private final TokenVerifier verifier;
    private final String applicationId;
    private final boolean requireTenant;
    public ResourceAccess(TokenVerifier verifier, String applicationId) {
        this(verifier,applicationId,false);
    }
    public ResourceAccess(TokenVerifier verifier, String applicationId,boolean requireTenant) {
        if (applicationId == null || applicationId.isBlank()) throw new IllegalArgumentException("Missing alisa.security.application-id");
        this.verifier = verifier; this.applicationId = applicationId;
        this.requireTenant=requireTenant;
    }
    public RequestIdentity authenticate(HttpServletRequest request) {
        RequestIdentity identity = verifier.verify(request.getHeader("Authorization"), applicationId);
        if(requireTenant && identity.tenantId().isBlank()) throw new AccessFailure(401,"Select a tenant first");
        if(!requireTenant && !identity.tenantId().isBlank()) throw new AccessFailure(401,"Tenant mode is not enabled on this backend");
        request.setAttribute(ATTRIBUTE, identity);
        return identity;
    }
    public void require(String type, String resourceId, String action) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            throw new IllegalStateException("No authenticated request");
        HttpServletRequest request = attributes.getRequest();
        try {
            RequestIdentity identity = (RequestIdentity) request.getAttribute(ATTRIBUTE);
            if (identity == null) identity = authenticate(request);
            verifier.require(identity, request.getHeader("Authorization"), applicationId, type, resourceId, action);
        } catch (AccessFailure exception) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(exception.status()), exception.getMessage());
        }
    }
}
