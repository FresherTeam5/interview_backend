package com.baseProject.myBaseProject.dto.notification;

import com.baseProject.myBaseProject.enums.UserNotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        UserNotificationType type,
        String title,
        String message,
        String resourceType,
        Long resourceId,
        Instant readAt,
        Instant createdAt) {
}
