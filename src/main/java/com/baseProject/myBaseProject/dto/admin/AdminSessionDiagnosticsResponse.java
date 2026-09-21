package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.time.Instant;

public record AdminSessionDiagnosticsResponse(
        Long sessionId,
        InterviewSessionStatus status,
        String preparationErrorCode,
        String preparationErrorMessage,
        String scoringErrorCode,
        String scoringErrorMessage,
        String planModelName,
        String planPromptVersion,
        String realtimeProvider,
        int currentTurnIndex,
        long adminPreparationRetries,
        long adminScoringRetries,
        Instant lastActivityAt,
        Instant updatedAt) {
}
