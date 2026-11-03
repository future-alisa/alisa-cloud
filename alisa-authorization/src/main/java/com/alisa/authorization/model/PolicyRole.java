package com.alisa.authorization.model;

public record PolicyRole(String applicationId, String code, String description) {
    public PolicyRole {
        Fields.text(applicationId, "application id", 64);
        Fields.text(code, "role code", 64);
        description = description == null ? "" : description;
        if (description.length() > 255) throw new IllegalArgumentException("Role description is too long");
    }
}
