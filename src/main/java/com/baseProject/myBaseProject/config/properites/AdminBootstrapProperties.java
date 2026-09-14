package com.baseProject.myBaseProject.config.properites;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin-bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String password,
        String fullName
) {
}
