package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.session.InterviewProgressResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionPageResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.entity.InterviewAssessment;
import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionNextAction;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewAssessmentRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaResultRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.service.InterviewHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewHistoryServiceImpl implements InterviewHistoryService {
    private static final int MIN_PROGRESS_DAYS = 7;
    private static final int MAX_PROGRESS_DAYS = 365;
    private static final int MAX_TREND_POINTS = 30;
    private static final int MAX_WEAK_AREAS = 5;

    private final InterviewSessionRepository sessions;
    private final InterviewAssessmentRepository assessments;
    private final InterviewFocusAreaResultRepository focusAreaResults;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public InterviewSessionPageResponse list(
            Long userId,
            String keyword,
            InterviewSessionStatus status,
            InterviewSessionMode mode,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        validateRange(createdFrom, createdTo);
        PageRequest pageable = pageRequest(page, size);
        Page<InterviewSession> result = sessions.searchForUser(
                userId,
                normalizeKeyword(keyword),
                status,
                mode,
                createdFrom,
                createdTo,
                pageable);

        List<Long> sessionIds = result.getContent().stream()
                .map(InterviewSession::getId)
                .toList();
        Map<Long, InterviewAssessment> scores = sessionIds.isEmpty()
                ? Map.of()
                : assessments.findBySessionIdIn(sessionIds).stream()
                        .collect(Collectors.toMap(
                                assessment -> assessment.getSession().getId(),
                                Function.identity()));

        List<InterviewSessionSummaryResponse> items = result.getContent().stream()
                .map(session -> toSummary(session, scores.get(session.getId())))
                .toList();
        return new InterviewSessionPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewProgressResponse progress(Long userId, int days) {
        if (days < MIN_PROGRESS_DAYS || days > MAX_PROGRESS_DAYS) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "days must be between " + MIN_PROGRESS_DAYS + " and " + MAX_PROGRESS_DAYS);
        }

        Instant to = clock.instant();
        Instant from = to.minus(days, ChronoUnit.DAYS);
        Instant previousFrom = from.minus(Duration.between(from, to));
        long total = sessions.countByUserIdAndCreatedAtBetween(userId, from, to);
        long completed = sessions.countByUserIdAndStatusAndCreatedAtBetween(
                userId, InterviewSessionStatus.COMPLETED, from, to);

        List<InterviewAssessment> current = assessments.findCompletedForUserBetween(
                userId, from, to);
        List<InterviewAssessment> previous = assessments.findCompletedForUserBetween(
                userId, previousFrom, from);
        List<InterviewFocusAreaResult> focusResults = focusAreaResults
                .findScoredForUserCompletedBetween(userId, from, to);

        InterviewProgressResponse.ScoreAverages averages = averages(current);
        BigDecimal previousOverall = average(
                previous.stream().map(InterviewAssessment::getOverallScore).toList());
        BigDecimal change = subtractNullable(averages.overall(), previousOverall);

        return new InterviewProgressResponse(
                from,
                to,
                days,
                total,
                completed,
                current.size(),
                percentage(completed, total),
                averages,
                change,
                trend(current),
                weakAreas(focusResults));
    }

    private InterviewSessionSummaryResponse toSummary(
            InterviewSession session,
            InterviewAssessment assessment) {
        return new InterviewSessionSummaryResponse(
                session.getId(),
                session.getStatus(),
                nextAction(session.getStatus()),
                session.getTemplateTitleSnapshot(),
                session.getProfileNameSnapshot(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getMode(),
                assessment == null ? null : assessment.getOverallScore(),
                assessment == null ? null : assessment.getTechnicalScore(),
                assessment == null ? null : assessment.getCommunicationScore(),
                session.getStartedAt(),
                session.getDeadlineAt(),
                session.getEndReason(),
                session.getEndedAt(),
                session.getCompletedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private InterviewSessionNextAction nextAction(InterviewSessionStatus status) {
        return switch (status) {
            case PREPARING -> InterviewSessionNextAction.WAIT_FOR_PREPARATION;
            case READY -> InterviewSessionNextAction.START;
            case PREPARATION_FAILED -> InterviewSessionNextAction.RETRY_PREPARATION;
            case IN_PROGRESS -> InterviewSessionNextAction.CONTINUE;
            case SCORING -> InterviewSessionNextAction.WAIT_FOR_SCORING;
            case SCORING_FAILED -> InterviewSessionNextAction.RETRY_SCORING;
            case COMPLETED -> InterviewSessionNextAction.VIEW_REPORT;
            case CANCELLED, EXPIRED -> InterviewSessionNextAction.NONE;
        };
    }

    private InterviewProgressResponse.ScoreAverages averages(
            List<InterviewAssessment> values) {
        return new InterviewProgressResponse.ScoreAverages(
                average(values.stream().map(InterviewAssessment::getOverallScore).toList()),
                average(values.stream().map(InterviewAssessment::getTechnicalScore).toList()),
                average(values.stream().map(InterviewAssessment::getCommunicationScore).toList()));
    }

    private List<InterviewProgressResponse.TrendPoint> trend(
            List<InterviewAssessment> values) {
        List<InterviewAssessment> newest = values.stream()
                .filter(value -> value.getSession().getCompletedAt() != null)
                .sorted(Comparator.comparing(
                        (InterviewAssessment value) -> value.getSession().getCompletedAt())
                        .reversed())
                .limit(MAX_TREND_POINTS)
                .collect(Collectors.toCollection(ArrayList::new));
        newest.sort(Comparator.comparing(value -> value.getSession().getCompletedAt()));
        return newest.stream()
                .map(value -> new InterviewProgressResponse.TrendPoint(
                        value.getSession().getId(),
                        value.getSession().getTemplateTitleSnapshot(),
                        value.getSession().getCompletedAt(),
                        value.getOverallScore(),
                        value.getTechnicalScore(),
                        value.getCommunicationScore()))
                .toList();
    }

    private List<InterviewProgressResponse.WeakFocusArea> weakAreas(
            List<InterviewFocusAreaResult> values) {
        Map<FocusAreaKey, ScoreAccumulator> grouped = new LinkedHashMap<>();
        for (InterviewFocusAreaResult value : values) {
            if (value.getScore() == null) {
                continue;
            }
            FocusAreaKey key = new FocusAreaKey(
                    value.getFocusArea().getCode(),
                    value.getFocusArea().getName());
            grouped.computeIfAbsent(key, ignored -> new ScoreAccumulator())
                    .add(value.getScore());
        }

        return grouped.entrySet().stream()
                .map(entry -> new InterviewProgressResponse.WeakFocusArea(
                        entry.getKey().code(),
                        entry.getKey().name(),
                        entry.getValue().average(),
                        entry.getValue().count))
                .sorted(Comparator.comparing(
                        InterviewProgressResponse.WeakFocusArea::averageScore))
                .limit(MAX_WEAK_AREAS)
                .toList();
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> available = values.stream().filter(Objects::nonNull).toList();
        if (available.isEmpty()) {
            return null;
        }
        BigDecimal sum = available.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(available.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal subtractNullable(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return null;
        }
        return current.subtract(previous).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "createdFrom must be before or equal to createdTo");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private record FocusAreaKey(String code, String name) {
    }

    private static final class ScoreAccumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private long count;

        private void add(BigDecimal value) {
            sum = sum.add(value);
            count++;
        }

        private BigDecimal average() {
            return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}
