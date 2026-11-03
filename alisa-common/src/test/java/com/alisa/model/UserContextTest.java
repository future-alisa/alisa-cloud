package com.alisa.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserContextTest {
    @Test void identityHeadersRoundTripUnicodeAndQuotedUsernames() {
        UserContext user = new UserContext();
        user.setUserId("existing-id");
        user.setUsername("用户\"alice\\name");
        UserContext decoded = UserContext.fromBase64Json(user.toBase64Json());
        assertNotNull(decoded);
        assertEquals(user.getUsername(), decoded.getUsername());
        assertEquals(user.getUserId(), decoded.getUserId());
    }
}
