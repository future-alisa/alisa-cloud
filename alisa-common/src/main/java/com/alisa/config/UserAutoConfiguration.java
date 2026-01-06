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
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new UserInterceptor()).addPathPatterns("/**");
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
            };
        }
    }
}