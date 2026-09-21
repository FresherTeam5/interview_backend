package com.baseProject.myBaseProject.dto.support;

import com.baseProject.myBaseProject.enums.SupportMessageVisibility;
import com.baseProject.myBaseProject.enums.UserRole;

import java.time.Instant;

public record SupportTicketMessageResponse(
        Long id,
        Long senderId,
        String senderName,
        UserRole senderRole,
        SupportMessageVisibility visibility,
        String message,
        Instant createdAt) {
}
