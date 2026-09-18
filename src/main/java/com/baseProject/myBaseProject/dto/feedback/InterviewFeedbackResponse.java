package com.baseProject.myBaseProject.dto.feedback;

import java.time.Instant;

public record InterviewFeedbackResponse(
        Long id,
        Long sessionId,
        Integer questionRating,
        Integer voiceRating,
        Integer reportRating,
        String comment,
        Instant createdAt,
        Instant updatedAt) {
}
