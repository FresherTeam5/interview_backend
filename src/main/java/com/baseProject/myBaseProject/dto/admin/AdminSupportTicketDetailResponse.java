package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;

import java.time.Instant;
import java.util.List;

public record AdminSupportTicketDetailResponse(
        Long id,
        String referenceCode,
        AdminUserReferenceResponse user,
        SupportTicketType type,
        SupportTicketStatus status,
        SupportTicketPriority priority,
        String subject,
        String description,
        AdminUserReferenceResponse assignedAdmin,
        Long sessionId,
        Long turnId,
        String contextJson,
        String resolutionSummary,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        List<SupportTicketMessageResponse> messages,
        List<AdminSupportTicketEventResponse> events) {
}
