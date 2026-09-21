package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;

import java.time.Instant;

public record AdminSupportTicketSummaryResponse(
        Long id,
        String referenceCode,
        AdminUserReferenceResponse user,
        SupportTicketType type,
        SupportTicketStatus status,
        SupportTicketPriority priority,
        String subject,
        AdminUserReferenceResponse assignedAdmin,
        Long sessionId,
        Instant createdAt,
        Instant updatedAt) {
}
