package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.InterviewAssessment;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionNextAction;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.repository.InterviewAssessmentRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaResultRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewHistoryServiceImplTest {
    private static final long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-18T08:00:00Z");

    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewAssessmentRepository assessments = mock(InterviewAssessmentRepository.class);
    private final InterviewFocusAreaResultRepository focusAreaResults =
            mock(InterviewFocusAreaResultRepository.class);
    private final InterviewHistoryServiceImpl service = new InterviewHistoryServiceImpl(
            sessions,
            assessments,
            focusAreaResults,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void listsOwnedSessionsWithScoresAndNextAction() {
        InterviewSession session = session(501L, InterviewSessionStatus.COMPLETED,
                NOW.minusSeconds(3600));
        InterviewAssessment assessment = assessment(
                session, "74.00", "75.00", "70.00");
        when(sessions.searchForUser(
                eq(USER_ID), eq("%backend%"), eq(InterviewSessionStatus.COMPLETED),
                eq(InterviewSessionMode.TURN_BASED), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(session)));
        when(assessments.findBySessionIdIn(List.of(501L)))
                .thenReturn(List.of(assessment));

        var result = service.list(
                USER_ID,
                " Backend ",
                InterviewSessionStatus.COMPLETED,
                InterviewSessionMode.TURN_BASED,
                null,
                null,
                0,
                20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(501L);
            assertThat(item.nextAction()).isEqualTo(InterviewSessionNextAction.VIEW_REPORT);
            assertThat(item.overallScore()).isEqualByComparingTo("74.00");
        });
    }

    @Test
    void aggregatesProgressAndWeakestFocusAreas() {
        Instant completedAt = NOW.minusSeconds(3600);
        InterviewSession firstSession = session(
                501L, InterviewSessionStatus.COMPLETED, completedAt.minusSeconds(60));
        InterviewSession secondSession = session(
                502L, InterviewSessionStatus.COMPLETED, completedAt);
        InterviewAssessment first = assessment(
                firstSession, "70.00", "72.00", "62.00");
        InterviewAssessment second = assessment(
                secondSession, "80.00", "82.00", "72.00");
        InterviewFocusArea backend = InterviewFocusArea.builder()
                .code("BACKEND")
                .name("Backend")
                .build();
        InterviewFocusArea communication = InterviewFocusArea.builder()
                .code("COMMUNICATION")
                .name("Communication")
                .build();

        when(sessions.countByUserIdAndCreatedAtBetween(eq(USER_ID), any(), eq(NOW)))
                .thenReturn(3L);
        when(sessions.countByUserIdAndStatusAndCreatedAtBetween(
                eq(USER_ID), eq(InterviewSessionStatus.COMPLETED), any(), eq(NOW)))
                .thenReturn(2L);
        when(assessments.findCompletedForUserBetween(eq(USER_ID), any(), eq(NOW)))
                .thenReturn(List.of(first, second));
        when(assessments.findCompletedForUserBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(first, second));
        when(assessments.findCompletedForUserBetween(
                eq(USER_ID), any(), eq(NOW.minus(30, java.time.temporal.ChronoUnit.DAYS))))
                .thenReturn(List.of(assessment(
                        session(500L, InterviewSessionStatus.COMPLETED,
                                NOW.minus(31, java.time.temporal.ChronoUnit.DAYS)),
                        "65.00", "66.00", "61.00")));
        when(focusAreaResults.findScoredForUserCompletedBetween(
                eq(USER_ID), any(), eq(NOW)))
                .thenReturn(List.of(
                        focusResult(first, backend, "75.00"),
                        focusResult(second, backend, "85.00"),
                        focusResult(first, communication, "60.00")));

        var result = service.progress(USER_ID, 30);

        assertThat(result.totalSessions()).isEqualTo(3);
        assertThat(result.completedSessions()).isEqualTo(2);
        assertThat(result.completionRate()).isEqualByComparingTo("66.67");
        assertThat(result.averages().overall()).isEqualByComparingTo("75.00");
        assertThat(result.overallChangeFromPreviousPeriod()).isEqualByComparingTo("10.00");
        assertThat(result.trend()).extracting(point -> point.sessionId())
                .containsExactly(501L, 502L);
        assertThat(result.weakFocusAreas()).first().satisfies(area -> {
            assertThat(area.code()).isEqualTo("COMMUNICATION");
            assertThat(area.averageScore()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    void rejectsInvalidDateRangeAndProgressWindow() {
        assertThatThrownBy(() -> service.list(
                USER_ID, null, null, null, NOW, NOW.minusSeconds(1), 0, 20))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.progress(USER_ID, 6))
                .isInstanceOf(DomainException.class);
    }

    private InterviewSession session(
            Long id,
            InterviewSessionStatus status,
            Instant completedAt) {
        return InterviewSession.builder()
                .id(id)
                .status(status)
                .mode(InterviewSessionMode.TURN_BASED)
                .templateTitleSnapshot("Backend Java")
                .profileNameSnapshot("Minh profile")
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .completedAt(completedAt)
                .createdAt(completedAt.minusSeconds(1800))
                .updatedAt(completedAt)
                .build();
    }

    private InterviewAssessment assessment(
            InterviewSession session,
            String overall,
            String technical,
            String communication) {
        return InterviewAssessment.builder()
                .session(session)
                .overallScore(new BigDecimal(overall))
                .technicalScore(new BigDecimal(technical))
                .communicationScore(new BigDecimal(communication))
                .build();
    }

    private InterviewFocusAreaResult focusResult(
            InterviewAssessment assessment,
            InterviewFocusArea focusArea,
            String score) {
        return InterviewFocusAreaResult.builder()
                .assessment(assessment)
                .focusArea(focusArea)
                .score(new BigDecimal(score))
                .build();
    }
}
