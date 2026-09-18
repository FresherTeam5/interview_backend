package com.baseProject.myBaseProject.dto.auth;

import java.time.Instant;

import com.baseProject.myBaseProject.enums.UserRole;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        String avatarUrl,
        UserRole role,
        Instant emailVerifiedAt,
        Instant deletionRequestedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
