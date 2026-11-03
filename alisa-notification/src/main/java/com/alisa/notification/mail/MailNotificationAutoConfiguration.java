package com.alisa.notification.mail;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@ConditionalOnClass(JavaMailSender.class)
@EnableConfigurationProperties(NotificationMailProperties.class)
public class MailNotificationAutoConfiguration {
    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnMissingBean(MailNotificationSender.class)
    public MailNotificationSender mailNotificationSender(JavaMailSender sender,
            NotificationMailProperties properties, Environment environment) {
        String from = properties.getFrom();
        if (from == null || from.isBlank()) from = environment.getProperty("spring.mail.username");
        return new SmtpMailNotificationSender(sender, from);
    }
}
