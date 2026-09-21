package com.baseProject.myBaseProject.dto.support;

import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;

import java.time.Instant;

public record SupportTicketResponse(
        Long id,
        String referenceCode,
        SupportTicketType type,
        SupportTicketStatus status,
        SupportTicketPriority priority,
        String subject,
        String description,
        Long sessionId,
        Long turnId,
        String contextJson,
        String resolutionSummary,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt) {
}
