package com.alisa.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("alisa.user")
public class IdentityProperties {
    private String applicationName = "Alisa";
    private String resetKeyPrefix = "alisa:user:reset:";
    private Duration resetCodeTtl = Duration.ofMinutes(5);
    private Duration resetSendInterval = Duration.ofSeconds(60);
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String value) { applicationName = value; }
    public String getResetKeyPrefix() { return resetKeyPrefix; }
    public void setResetKeyPrefix(String value) { resetKeyPrefix = value; }
    public Duration getResetCodeTtl() { return resetCodeTtl; }
    public void setResetCodeTtl(Duration value) { resetCodeTtl = value; }
    public Duration getResetSendInterval() { return resetSendInterval; }
    public void setResetSendInterval(Duration value) { resetSendInterval = value; }
}
