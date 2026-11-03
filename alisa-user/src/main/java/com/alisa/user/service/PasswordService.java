package com.alisa.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public String encode(String raw) {
        if (raw == null || raw.isBlank() || raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码不能为空，且 UTF-8 长度不能超过 72 字节");
        }
        return encoder.encode(raw);
    }
    public boolean isEncoded(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
    public boolean matches(String raw, String stored) {
        if (raw == null || raw.isEmpty() || raw.getBytes(StandardCharsets.UTF_8).length > 72
                || stored == null || stored.isEmpty()) return false;
        if (isEncoded(stored)) return encoder.matches(raw, stored);
        // Existing accounts keep their IDs and credentials; upgrade only after a successful login.
        return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }
}
