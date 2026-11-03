package com.alisa.notification.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("alisa.notification.mail")
public class NotificationMailProperties {
    private String from;
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}
