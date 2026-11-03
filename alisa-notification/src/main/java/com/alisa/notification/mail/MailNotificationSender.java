package com.alisa.notification.mail;

/** Generic transport port: callers own message templates and business decisions. */
public interface MailNotificationSender {
    void send(String recipient, String subject, String body);
}
