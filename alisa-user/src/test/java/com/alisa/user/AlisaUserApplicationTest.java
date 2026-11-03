package com.alisa.user;

import com.alisa.notification.mail.MailNotificationSender;
import com.alisa.user.mapper.UserMapper;
import com.alisa.user.service.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.config.import=", "spring.profiles.active=unit", "alisa.authorization.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "jwt.secret=dGVzdC1rZXktb25seS0wMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NQ=="
})
class AlisaUserApplicationTest {
    @MockBean UserMapper users;
    @MockBean com.alisa.user.mapper.ExternalIdentityMapper identities;
    @MockBean StringRedisTemplate redis;
    @MockBean JavaMailSender mail;
    @Autowired ApplicationContext context;

    @Test void standaloneAccountServiceWiresTheReusableNotificationLibrary() {
        assertNotNull(context.getBean(IdentityService.class));
        assertNotNull(context.getBean(MailNotificationSender.class));
        assertEquals("user-service", context.getEnvironment().getProperty("spring.application.name"));
    }
}
