package com.baseProject.myBaseProject.dto.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InterviewProgressResponse(
        Instant periodFrom,
        Instant periodTo,
        int periodDays,
        long totalSessions,
        long completedSessions,
        long scoredSessions,
        BigDecimal completionRate,
        ScoreAverages averages,
        BigDecimal overallChangeFromPreviousPeriod,
        List<TrendPoint> trend,
        List<WeakFocusArea> weakFocusAreas) {

    public record ScoreAverages(
            BigDecimal overall,
            BigDecimal technical,
            BigDecimal communication) {
    }

    public record TrendPoint(
            Long sessionId,
            String templateTitle,
            Instant completedAt,
            BigDecimal overall,
            BigDecimal technical,
            BigDecimal communication) {
    }

    public record WeakFocusArea(
            String code,
            String name,
            BigDecimal averageScore,
            long scoredSessions) {
    }
}
