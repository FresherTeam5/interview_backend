package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminAnalyticsResponse;
import com.baseProject.myBaseProject.dto.admin.AdminAnalyticsTimeSeriesResponse;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public AdminAnalyticsResponse summary(Instant requestedFrom, Instant requestedTo) {
        Range range = range(requestedFrom, requestedTo);
        Instant from = range.from();
        Instant to = range.to();

        long totalUsers = number("SELECT COUNT(*) FROM user_accounts WHERE role = 'USER'");
        long newUsers = number("""
                SELECT COUNT(*) FROM user_accounts
                WHERE role = 'USER' AND created_at >= ? AND created_at < ?
                """, from, to);
        long verifiedUsers = number("""
                SELECT COUNT(*) FROM user_accounts
                WHERE role = 'USER' AND email_verified_at IS NOT NULL
                """);
        long activeUsers = number("""
                SELECT COUNT(*) FROM user_accounts
                WHERE role = 'USER' AND last_login_at >= ? AND last_login_at < ?
                """, from, to);

        Map<String, Object> session = row("""
                SELECT COUNT(*) AS created_count,
                       COALESCE(SUM(prepared_at IS NOT NULL), 0) AS prepared_count,
                       COALESCE(SUM(started_at IS NOT NULL), 0) AS started_count,
                       COALESCE(SUM(completed_at IS NOT NULL), 0) AS completed_count,
                       COALESCE(SUM(status = 'PREPARATION_FAILED'), 0) AS preparation_failed_count,
                       COALESCE(SUM(status = 'SCORING_FAILED'), 0) AS scoring_failed_count,
                       AVG(CASE WHEN completed_at IS NOT NULL
                           THEN TIMESTAMPDIFF(SECOND, created_at, completed_at) / 60.0 END)
                           AS average_completion_minutes
                FROM interview_sessions
                WHERE created_at >= ? AND created_at < ?
                """, from, to);
        long createdSessions = longValue(session, "created_count");
        long completedSessions = longValue(session, "completed_count");

        Map<String, Object> support = row("""
                SELECT COUNT(*) AS created_count,
                       COALESCE(SUM(status IN ('OPEN', 'IN_REVIEW')), 0) AS open_count,
                       COALESCE(SUM(status = 'RESOLVED'), 0) AS resolved_count,
                       COALESCE(SUM(status = 'CLOSED'), 0) AS closed_count,
                       AVG(CASE WHEN resolved_at IS NOT NULL
                           THEN TIMESTAMPDIFF(SECOND, created_at, resolved_at) / 3600.0 END)
                           AS average_resolution_hours
                FROM support_tickets
                WHERE created_at >= ? AND created_at < ?
                """, from, to);

        Map<String, Object> feedback = row("""
                SELECT COUNT(*) AS response_count,
                       AVG(question_rating) AS average_question_rating,
                       AVG(voice_rating) AS average_voice_rating,
                       AVG(report_rating) AS average_report_rating
                FROM interview_feedback
                WHERE created_at >= ? AND created_at < ?
                """, from, to);

        Map<String, Object> template = row("""
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(moderation_status = 'PENDING_REVIEW'), 0) AS pending_count,
                       COALESCE(SUM(published_at IS NOT NULL AND archived_at IS NULL), 0)
                           AS published_count,
                       COALESCE(SUM(featured = TRUE AND published_at IS NOT NULL
                           AND archived_at IS NULL), 0) AS featured_count
                FROM interview_templates
                """);

        return new AdminAnalyticsResponse(clock.instant(), from, to,
                new AdminAnalyticsResponse.UserMetrics(
                        totalUsers, newUsers, verifiedUsers, activeUsers),
                new AdminAnalyticsResponse.SessionFunnel(
                        createdSessions,
                        longValue(session, "prepared_count"),
                        longValue(session, "started_count"),
                        completedSessions,
                        longValue(session, "preparation_failed_count"),
                        longValue(session, "scoring_failed_count"),
                        percentage(completedSessions, createdSessions),
                        decimal(session, "average_completion_minutes")),
                new AdminAnalyticsResponse.SupportMetrics(
                        longValue(support, "created_count"),
                        longValue(support, "open_count"),
                        longValue(support, "resolved_count"),
                        longValue(support, "closed_count"),
                        decimal(support, "average_resolution_hours")),
                new AdminAnalyticsResponse.FeedbackMetrics(
                        longValue(feedback, "response_count"),
                        decimal(feedback, "average_question_rating"),
                        decimal(feedback, "average_voice_rating"),
                        decimal(feedback, "average_report_rating")),
                new AdminAnalyticsResponse.TemplateMetrics(
                        longValue(template, "total_count"),
                        longValue(template, "pending_count"),
                        longValue(template, "published_count"),
                        longValue(template, "featured_count"),
                        number("SELECT COALESCE(SUM(view_count), 0) FROM user_template_views"),
                        number("SELECT COUNT(*) FROM user_template_favorites")));
    }

    @Override
    public AdminAnalyticsTimeSeriesResponse timeSeries(
            Instant requestedFrom, Instant requestedTo) {
        Range range = range(requestedFrom, requestedTo);
        LocalDate first = range.from().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate last = range.to().minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, MutableDay> days = new LinkedHashMap<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            days.put(day, new MutableDay());
        }

        apply(days, """
                SELECT DATE(created_at) AS metric_date, COUNT(*) AS metric_count
                FROM user_accounts
                WHERE role = 'USER' AND created_at >= ? AND created_at < ?
                GROUP BY DATE(created_at)
                ORDER BY metric_date
                """, range, Metric.NEW_USERS);
        apply(days, """
                SELECT DATE(created_at) AS metric_date, COUNT(*) AS metric_count
                FROM interview_sessions
                WHERE created_at >= ? AND created_at < ?
                GROUP BY DATE(created_at)
                ORDER BY metric_date
                """, range, Metric.SESSIONS_CREATED);
        apply(days, """
                SELECT DATE(completed_at) AS metric_date, COUNT(*) AS metric_count
                FROM interview_sessions
                WHERE completed_at >= ? AND completed_at < ?
                GROUP BY DATE(completed_at)
                ORDER BY metric_date
                """, range, Metric.SESSIONS_COMPLETED);
        apply(days, """
                SELECT DATE(updated_at) AS metric_date, COUNT(*) AS metric_count
                FROM interview_sessions
                WHERE updated_at >= ? AND updated_at < ?
                  AND status IN ('PREPARATION_FAILED', 'SCORING_FAILED')
                GROUP BY DATE(updated_at)
                ORDER BY metric_date
                """, range, Metric.SESSIONS_FAILED);
        apply(days, """
                SELECT DATE(created_at) AS metric_date, COUNT(*) AS metric_count
                FROM support_tickets
                WHERE created_at >= ? AND created_at < ?
                GROUP BY DATE(created_at)
                ORDER BY metric_date
                """, range, Metric.SUPPORT_CREATED);

        List<AdminAnalyticsTimeSeriesResponse.Day> response = days.entrySet().stream()
                .map(entry -> entry.getValue().response(entry.getKey()))
                .toList();
        return new AdminAnalyticsTimeSeriesResponse(range.from(), range.to(), response);
    }

    @Override
    public byte[] exportCsv(Instant from, Instant to) {
        AdminAnalyticsTimeSeriesResponse series = timeSeries(from, to);
        StringBuilder csv = new StringBuilder(
                "date,new_users,sessions_created,sessions_completed,sessions_failed,support_tickets_created\n");
        series.days().forEach(day -> csv.append(day.date()).append(',')
                .append(day.newUsers()).append(',')
                .append(day.sessionsCreated()).append(',')
                .append(day.sessionsCompleted()).append(',')
                .append(day.sessionsFailed()).append(',')
                .append(day.supportTicketsCreated()).append('\n'));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void apply(
            Map<LocalDate, MutableDay> days, String sql, Range range, Metric metric) {
        jdbc.query(sql, resultSet -> {
            LocalDate date = resultSet.getDate("metric_date").toLocalDate();
            MutableDay day = days.get(date);
            if (day != null) {
                day.set(metric, resultSet.getLong("metric_count"));
            }
        }, range.from(), range.to());
    }

    private Range range(Instant requestedFrom, Instant requestedTo) {
        Instant to = requestedTo == null ? clock.instant() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(DEFAULT_DAYS, ChronoUnit.DAYS)
                : requestedFrom;
        if (!from.isBefore(to) || to.isAfter(from.plus(MAX_DAYS, ChronoUnit.DAYS))) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Analytics range must be positive and no longer than 366 days");
        }
        return new Range(from, to);
    }

    private long number(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private Map<String, Object> row(String sql, Object... arguments) {
        return jdbc.queryForMap(sql, arguments);
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long portion, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(portion).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private enum Metric {
        NEW_USERS,
        SESSIONS_CREATED,
        SESSIONS_COMPLETED,
        SESSIONS_FAILED,
        SUPPORT_CREATED
    }

    private static final class MutableDay {
        private long newUsers;
        private long sessionsCreated;
        private long sessionsCompleted;
        private long sessionsFailed;
        private long supportCreated;

        private void set(Metric metric, long value) {
            switch (metric) {
                case NEW_USERS -> newUsers = value;
                case SESSIONS_CREATED -> sessionsCreated = value;
                case SESSIONS_COMPLETED -> sessionsCompleted = value;
                case SESSIONS_FAILED -> sessionsFailed = value;
                case SUPPORT_CREATED -> supportCreated = value;
            }
        }

        private AdminAnalyticsTimeSeriesResponse.Day response(LocalDate date) {
            return new AdminAnalyticsTimeSeriesResponse.Day(date, newUsers, sessionsCreated,
                    sessionsCompleted, sessionsFailed, supportCreated);
        }
    }

    private record Range(Instant from, Instant to) {
    }
}
