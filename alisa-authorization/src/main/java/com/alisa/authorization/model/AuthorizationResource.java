package com.alisa.authorization.model;

public record AuthorizationResource(String type, String id) {
    public AuthorizationResource {
        Fields.text(type, "resource type", 64);
        Fields.text(id, "resource id", 191);
    }
}
