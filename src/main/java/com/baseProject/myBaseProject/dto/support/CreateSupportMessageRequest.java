package com.baseProject.myBaseProject.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportMessageRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message must not exceed 5000 characters")
        String message) {
}
