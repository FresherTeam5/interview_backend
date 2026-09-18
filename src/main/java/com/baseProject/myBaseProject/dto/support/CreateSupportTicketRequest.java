package com.baseProject.myBaseProject.dto.support;

import com.baseProject.myBaseProject.enums.SupportTicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotNull(message = "Support ticket type is required")
        SupportTicketType type,
        @NotBlank(message = "Support ticket subject is required")
        @Size(max = 200, message = "Support ticket subject must not exceed 200 characters")
        String subject,
        @NotBlank(message = "Support ticket description is required")
        @Size(max = 5000, message = "Support ticket description must not exceed 5000 characters")
        String description,
        Long sessionId,
        Long turnId) {
}
