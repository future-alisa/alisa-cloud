package com.alisa.authorization.config;

import java.util.List;
import com.alisa.authorization.AuthorizationEngine;
import com.alisa.authorization.local.JdbcAuthorizationPolicyStore;
import com.alisa.authorization.local.LocalAuthorizationProvider;
import com.alisa.authorization.spi.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration")
@ConditionalOnProperty(prefix = "alisa.authorization", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuthorizationProperties.class)
public class AuthorizationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuthorizationEngine.class)
    public AuthorizationEngine authorizationEngine(List<AuthorizationProvider> providers, AuthorizationProperties properties) {
        return new AuthorizationEngine(providers, properties.getApplications());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    public static class JdbcPolicies {
        @Bean
        @ConditionalOnBean(JdbcTemplate.class)
        @ConditionalOnMissingBean(AuthorizationPolicyStore.class)
        public AuthorizationPolicyStore authorizationPolicyStore(JdbcTemplate jdbc) {
            return new JdbcAuthorizationPolicyStore(jdbc);
        }
    }

    @Bean
    @ConditionalOnBean(AuthorizationPolicyStore.class)
    public LocalAuthorizationProvider localAuthorizationProvider(AuthorizationPolicyStore store) {
        return new LocalAuthorizationProvider(store);
    }
}
