package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.TemplateModerationStatus;

import java.time.Instant;

public record AdminTemplateSummaryResponse(
        Long id,
        AdminUserReferenceResponse owner,
        String title,
        String jobTitle,
        String targetSeniority,
        TemplateModerationStatus moderationStatus,
        boolean confirmed,
        boolean published,
        boolean featured,
        String category,
        String tagsJson,
        Instant submittedAt,
        Instant reviewedAt,
        String moderationReason,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
