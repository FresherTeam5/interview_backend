package com.baseProject.myBaseProject.dto.template;

import java.time.Instant;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;

public record InterviewTemplateResponse(
        Long id,
        Long sourceJobDescriptionId,
        String title,
        String jobTitle,
        String targetSeniority,
        JobAnalysis content,
        boolean confirmed,
        Instant confirmedAt,
        boolean published,
        Instant publishedAt,
        TemplateModerationStatus moderationStatus,
        Instant submittedAt,
        Instant reviewedAt,
        String moderationReason,
        String category,
        String tagsJson,
        boolean featured,
        int displayOrder,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
