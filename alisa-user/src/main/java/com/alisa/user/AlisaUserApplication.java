package com.alisa.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlisaUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlisaUserApplication.class, args);
    }
}
