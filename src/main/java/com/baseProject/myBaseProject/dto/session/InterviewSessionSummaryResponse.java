package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionNextAction;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

import java.math.BigDecimal;
import java.time.Instant;

public record InterviewSessionSummaryResponse(
        Long id,
        InterviewSessionStatus status,
        InterviewSessionNextAction nextAction,
        String templateTitle,
        String profileName,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        InterviewSessionMode mode,
        BigDecimal overallScore,
        BigDecimal technicalScore,
        BigDecimal communicationScore,
        Instant startedAt,
        Instant deadlineAt,
        InterviewEndReason endReason,
        Instant endedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
