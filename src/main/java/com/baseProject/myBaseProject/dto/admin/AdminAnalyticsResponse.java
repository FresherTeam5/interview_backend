package com.baseProject.myBaseProject.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminAnalyticsResponse(
        Instant generatedAt,
        Instant from,
        Instant to,
        UserMetrics users,
        SessionFunnel sessions,
        SupportMetrics support,
        FeedbackMetrics feedback,
        TemplateMetrics templates) {

    public record UserMetrics(long total, long newUsers, long verifiedUsers, long activeUsers) {
    }

    public record SessionFunnel(
            long created,
            long prepared,
            long started,
            long completed,
            long preparationFailed,
            long scoringFailed,
            BigDecimal completionRate,
            BigDecimal averageCompletionMinutes) {
    }

    public record SupportMetrics(
            long created,
            long open,
            long resolved,
            long closed,
            BigDecimal averageFirstResolutionHours) {
    }

    public record FeedbackMetrics(
            long responses,
            BigDecimal averageQuestionRating,
            BigDecimal averageVoiceRating,
            BigDecimal averageReportRating) {
    }

    public record TemplateMetrics(
            long total,
            long pendingReview,
            long published,
            long featured,
            long totalViews,
            long totalFavorites) {
    }
}
