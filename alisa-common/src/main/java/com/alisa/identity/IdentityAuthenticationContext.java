package com.alisa.identity;

/** Trusted server context, not accepted from a request body. */
public record IdentityAuthenticationContext(Purpose purpose, String userId) {
    public enum Purpose { SIGN_IN, LINK }

    public IdentityAuthenticationContext {
        if (purpose == null) throw new IllegalArgumentException("Missing authentication purpose");
        if (purpose == Purpose.LINK && (userId == null || userId.isBlank()))
            throw new IllegalArgumentException("Linking requires an authenticated user");
        if (purpose == Purpose.SIGN_IN && userId != null)
            throw new IllegalArgumentException("A sign-in cannot assert an internal user id");
    }

    public static IdentityAuthenticationContext signIn() {
        return new IdentityAuthenticationContext(Purpose.SIGN_IN, null);
    }
    public static IdentityAuthenticationContext link(String userId) {
        return new IdentityAuthenticationContext(Purpose.LINK, userId);
    }
}
