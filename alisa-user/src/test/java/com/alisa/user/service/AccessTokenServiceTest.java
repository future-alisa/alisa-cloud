package com.alisa.user.service;

import java.util.Base64;
import com.alisa.config.JwtProperties;
import com.alisa.util.JwtUtil;
import com.alisa.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class AccessTokenServiceTest {
    @Test void tokensKeepTheGatewaySubjectAndUserIdContract() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString("test-key-only-01234567890123456789012345".getBytes()));
        new JwtUtil().setJwtProperties(properties);
        User account = new User(); account.setId("existing-id"); account.setUsername("alice");
        AccessTokenService service = new AccessTokenService();
        String token = service.issue(account);
        assertEquals("alice", JwtUtil.extractUsername(token));
        assertEquals("existing-id", JwtUtil.extractAllClaims(token).get("userId"));
        assertEquals("existing-id", service.requireUserId("Bearer " + token));
        assertThrows(ResponseStatusException.class, () -> service.requireUserId(null));
        assertThrows(ResponseStatusException.class, () -> service.requireUserId("Bearer tampered"));
    }
}
