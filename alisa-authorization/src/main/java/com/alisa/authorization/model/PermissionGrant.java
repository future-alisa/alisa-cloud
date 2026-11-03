package com.alisa.authorization.model;

/** Only resourceId="*" is a wildcard. Application, role, type and action are exact. */
public record PermissionGrant(String applicationId, String roleCode, String resourceType,
        String resourceId, String action) {
    public PermissionGrant {
        Fields.text(applicationId, "application id", 64);
        Fields.text(roleCode, "role code", 64);
        Fields.text(resourceType, "resource type", 64);
        Fields.text(resourceId, "resource id", 191);
        Fields.text(action, "action", 64);
    }
}
