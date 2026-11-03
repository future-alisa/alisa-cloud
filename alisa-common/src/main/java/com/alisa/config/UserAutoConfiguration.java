package com.alisa.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alisa.interceptors.UserInterceptor;
import com.alisa.model.UserContext;

@Configuration
public class UserAutoConfiguration {

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class MvcUserConfig implements WebMvcConfigurer {
        private final org.springframework.beans.factory.ObjectProvider<com.alisa.security.ResourceAccess> access;
        public MvcUserConfig(org.springframework.beans.factory.ObjectProvider<com.alisa.security.ResourceAccess> access) { this.access = access; }
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new UserInterceptor(access.getIfAvailable())).addPathPatterns("/**").excludePathPatterns("/error", "/actuator/health");
        }
    }

    @Configuration
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    public static class FeignConfiguration {
        @Bean
        public feign.RequestInterceptor feignUserInterceptor() {
            return template -> {
                var user = UserContext.get();
                if (user != null) {
                    template.header("X-User-Header", user.toBase64Json());
                }
                if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                        instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
                    String token = attributes.getRequest().getHeader("Authorization");
                    if (token != null) template.header("Authorization", token);
                }
            };
        }
    }
}
