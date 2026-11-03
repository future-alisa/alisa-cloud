package com.alisa.identity;

/** Provider-specific protocol failures should be translated to this exception. */
public class AuthenticationRejectedException extends RuntimeException {
    public AuthenticationRejectedException() {
        super("Authentication rejected");
    }
}
