package com.alisa.identity;

import java.util.Map;

public record IdentityCredentials(String method, Map<String, String> values) {
    public IdentityCredentials {
        if (method == null || method.isBlank() || method.length() > 64)
            throw new IllegalArgumentException("Invalid authentication method");
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    @Override
    public String toString() {
        return "IdentityCredentials[method=" + method + ", values=REDACTED]";
    }
}
