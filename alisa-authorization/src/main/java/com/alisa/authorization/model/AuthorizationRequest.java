package com.alisa.authorization.model;

import java.util.Map;
import java.util.Objects;

public record AuthorizationRequest(String applicationId, AuthorizationSubject subject,
        AuthorizationResource resource, String action, Map<String, String> context) {
    public AuthorizationRequest {
        Fields.text(applicationId, "application id", 64);
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(resource, "resource");
        Fields.text(action, "action", 64);
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
