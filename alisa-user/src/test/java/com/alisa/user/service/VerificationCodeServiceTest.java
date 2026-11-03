package com.alisa.user.service;

import java.time.Duration;
import java.util.List;
import com.alisa.notification.mail.MailNotificationSender;
import com.alisa.user.config.IdentityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationCodeServiceTest {
    StringRedisTemplate redis;
    ValueOperations<String,String> values;
    MailNotificationSender mail;
    VerificationCodeService service;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        mail = mock(MailNotificationSender.class); when(redis.opsForValue()).thenReturn(values);
        service = new VerificationCodeService(redis, mail, new IdentityProperties());
    }
    @Test void sendsSixDigitCodesWithExpiryAndASendThrottle() {
        when(values.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
        service.send("alice@example.test");
        var code = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("alisa:user:reset:alice@example.test:code"), code.capture(), eq(Duration.ofMinutes(5)));
        assertTrue(code.getValue().matches("\\d{6}"));
        verify(mail).send(eq("alice@example.test"), eq("【Alisa】密码重置验证码"), contains(code.getValue()));
    }
    @Test void throttledRequestsNeverOverwriteCodesOrSendMail() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        var error = assertThrows(ResponseStatusException.class, () -> service.send("alice@example.test"));
        assertEquals(429, error.getStatusCode().value());
        verifyNoInteractions(mail);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
    @Test void failedDeliveryRemovesTheCodeAndThrottle() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new MailSendException("smtp details")).when(mail).send(anyString(), anyString(), anyString());
        assertThrows(MailSendException.class, () -> service.send("alice@example.test"));
        verify(redis).delete(List.of("alisa:user:reset:alice@example.test:code",
                "alisa:user:reset:alice@example.test:send", "alisa:user:reset:alice@example.test:attempts"));
    }
    @Test void consumeUsesAtomicRedisScriptAndRejectsMalformedCodesWithoutRedis() {
        assertFalse(service.consume("alice@example.test", "bad"));
        verifyNoInteractions(redis);
        when(redis.execute(any(), anyList(), eq("123456"))).thenReturn(1L);
        assertTrue(service.consume("alice@example.test", "123456"));
        when(redis.execute(any(), anyList(), eq("123456"))).thenReturn(0L);
        assertFalse(service.consume("alice@example.test", "123456"));
    }
}
