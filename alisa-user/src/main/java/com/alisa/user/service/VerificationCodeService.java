package com.alisa.user.service;

import java.security.SecureRandom;
import java.util.List;
import com.alisa.notification.mail.MailNotificationSender;
import com.alisa.user.config.IdentityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificationCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    // Atomic compare-and-consume, with five attempts maximum and no TTL extension.
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        local code = redis.call('GET', KEYS[1])
        if not code then return 0 end
        local attempts = redis.call('INCR', KEYS[2])
        if attempts == 1 then
          local ttl = redis.call('PTTL', KEYS[1])
          if ttl > 0 then redis.call('PEXPIRE', KEYS[2], ttl) else redis.call('DEL', KEYS[1], KEYS[2]); return 0 end
        end
        if code == ARGV[1] and attempts <= 5 then redis.call('DEL', KEYS[1], KEYS[2]); return 1 end
        if attempts >= 5 then redis.call('DEL', KEYS[1], KEYS[2]) end
        return 0
        """, Long.class);
    private final StringRedisTemplate redis;
    private final MailNotificationSender mail;
    private final IdentityProperties properties;
    public VerificationCodeService(StringRedisTemplate redis, MailNotificationSender mail, IdentityProperties properties) {
        this.redis = redis; this.mail = mail; this.properties = properties;
    }
    public void send(String email) {
        String key = properties.getResetKeyPrefix() + email;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":send", "1", properties.getResetSendInterval()))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            redis.opsForValue().set(key + ":code", code, properties.getResetCodeTtl());
            redis.delete(key + ":attempts");
            mail.send(email, "【" + properties.getApplicationName() + "】密码重置验证码",
                    "您的验证码为：" + code + "\n有效期 " + properties.getResetCodeTtl().toMinutes()
                    + " 分钟，请勿泄露给他人。如非本人操作，请忽略此邮件。");
        } catch (RuntimeException exception) {
            redis.delete(List.of(key + ":code", key + ":send", key + ":attempts"));
            throw exception;
        }
    }
    public boolean consume(String email, String code) {
        if (email == null || code == null || !code.matches("\\d{6}")) return false;
        String key = properties.getResetKeyPrefix() + email;
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(key + ":code", key + ":attempts"), code));
    }
}
