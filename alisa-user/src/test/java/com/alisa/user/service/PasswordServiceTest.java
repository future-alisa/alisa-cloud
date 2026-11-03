package com.alisa.user.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {
    @Test void rejectsOverlongUtf8ProofsRatherThanAcceptingATruncatedBcryptPassword() {
        PasswordService service = new PasswordService();
        String password = "a".repeat(72);
        String encoded = service.encode(password);
        assertTrue(service.matches(password, encoded));
        assertFalse(service.matches(password + "different-suffix", encoded));
        assertFalse(service.matches("密".repeat(25), encoded));
    }
}
