package com.alisa.authorization.model;

public final class Fields {
    private Fields() {}
    public static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength)
            throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}
