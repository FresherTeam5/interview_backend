package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.account")
public record AccountProperties(
        @Positive long emailVerificationTtlMinutes,
        @Positive long passwordResetTtlMinutes,
        @Positive int deletionGraceDays,
        @NotBlank String frontendBaseUrl,
        boolean mailEnabled,
        @NotBlank String mailFrom) {
}
