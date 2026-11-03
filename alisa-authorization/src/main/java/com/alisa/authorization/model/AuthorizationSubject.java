package com.alisa.authorization.model;

public record AuthorizationSubject(String type, String id) {
    public AuthorizationSubject {
        Fields.text(type, "subject type", 32);
        Fields.text(id, "subject id", 191);
    }

    public static AuthorizationSubject user(String id) {
        return new AuthorizationSubject("USER", id);
    }
}
