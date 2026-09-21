package com.baseProject.myBaseProject.dto.template;

import java.time.Instant;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;

public record InterviewTemplateSummaryResponse(
        Long id,
        Long sourceJobDescriptionId,
        String title,
        String jobTitle,
        String targetSeniority,
        boolean confirmed,
        boolean published,
        TemplateModerationStatus moderationStatus,
        String moderationReason,
        String category,
        String tagsJson,
        boolean featured,
        Instant archivedAt,
        Instant updatedAt) {
}
