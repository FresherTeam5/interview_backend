package com.baseProject.myBaseProject.dto.account;

import com.baseProject.myBaseProject.enums.UserRole;
import java.time.Instant;

public record AccountProfileResponse(
        Long id,
        String fullName,
        String email,
        String avatarUrl,
        UserRole role,
        Instant emailVerifiedAt,
        Preferences preferences,
        Instant deletionRequestedAt,
        Instant createdAt,
        Instant updatedAt) {
    public record Preferences(
            String languageCode,
            String timeZone,
            boolean emailNotifications,
            boolean processingNotifications) {
    }
}
