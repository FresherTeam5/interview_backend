package com.baseProject.myBaseProject.dto.support;

import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;

import java.time.Instant;

public record SupportTicketResponse(
        Long id,
        String referenceCode,
        SupportTicketType type,
        SupportTicketStatus status,
        String subject,
        String description,
        Long sessionId,
        Long turnId,
        String contextJson,
        Instant createdAt,
        Instant updatedAt) {
}
