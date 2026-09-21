package com.baseProject.myBaseProject.dto.admin;

import java.time.Instant;

public record AdminInterviewFeedbackResponse(
        Long id,
        Long sessionId,
        AdminUserReferenceResponse user,
        Integer questionRating,
        Integer voiceRating,
        Integer reportRating,
        String comment,
        Instant createdAt,
        Instant updatedAt) {
}
