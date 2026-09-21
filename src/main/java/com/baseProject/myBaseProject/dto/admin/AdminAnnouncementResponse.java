package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;

import java.time.Instant;

public record AdminAnnouncementResponse(
        Long id,
        AdminUserReferenceResponse createdBy,
        String title,
        String message,
        AnnouncementAudience audience,
        boolean inAppEnabled,
        boolean emailEnabled,
        AnnouncementStatus status,
        Instant scheduledAt,
        Instant startedAt,
        Instant completedAt,
        long totalRecipients,
        long deliveredCount,
        long failedCount,
        String lastError,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
