package com.example.crudoperation.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecuritySettings(String jwtSecret, String adminUsername, String adminPassword,
                               String userUsername, String userPassword) {
}
