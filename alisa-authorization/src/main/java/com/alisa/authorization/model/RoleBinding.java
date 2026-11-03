package com.alisa.authorization.model;

import java.util.Objects;

public record RoleBinding(String applicationId, String roleCode, AuthorizationSubject subject) {
    public RoleBinding {
        Fields.text(applicationId, "application id", 64);
        Fields.text(roleCode, "role code", 64);
        Objects.requireNonNull(subject, "subject");
    }
}
