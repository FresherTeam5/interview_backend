package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.SupportTicketEventType;

import java.time.Instant;

public record AdminSupportTicketEventResponse(
        Long id,
        AdminUserReferenceResponse actor,
        SupportTicketEventType type,
        String fromValue,
        String toValue,
        String note,
        Instant createdAt) {
}
