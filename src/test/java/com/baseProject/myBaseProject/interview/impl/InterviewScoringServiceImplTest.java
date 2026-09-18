package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.entity.InterviewAssessment;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewScoringEngine;
import com.baseProject.myBaseProject.interview.model.InterviewScoreCalculation;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.support.InterviewScoreCalculator;
import com.baseProject.myBaseProject.interview.support.InterviewScoringContextLoader;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewAssessmentRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaResultRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewScoringServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-07T08:00:00Z");
    private static final Long SESSION_ID = 501L;

    @Test
    void persistsAssessmentAndCompletesSession() {
        Fixture fixture = new Fixture(scoringSession());
        when(fixture.contextLoader.load(SESSION_ID)).thenReturn(context());
        when(fixture.engine.assess(any())).thenReturn(assessmentResult());
        when(fixture.calculator.calculate(any(), any())).thenReturn(calculation());
        when(fixture.focusAreas.findBySessionIdOrderByDisplayOrderAsc(SESSION_ID))
                .thenReturn(List.of(fixture.area));
        when(fixture.assessments.save(any())).thenAnswer(invocation -> {
            InterviewAssessment assessment = invocation.getArgument(0);
            ReflectionTestUtils.setField(assessment, "id", 91L);
            return assessment;
        });

        fixture.service.scoreAsync(SESSION_ID);

        assertThat(fixture.session.getStatus()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(fixture.session.getScoringStartedAt()).isEqualTo(NOW);
        assertThat(fixture.session.getCompletedAt()).isEqualTo(NOW);
        verify(fixture.assessments).save(any(InterviewAssessment.class));
        verify(fixture.focusAreaResults).saveAll(any());
        verify(fixture.transitions).record(
                fixture.session,
                InterviewSessionStatus.SCORING,
                InterviewSessionStatus.COMPLETED,
                "Interview scoring completed",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.SYSTEM,
                NOW);
    }

    @Test
    void marksSessionFailedWhenAiTimesOut() {
        Fixture fixture = new Fixture(scoringSession());
        when(fixture.contextLoader.load(SESSION_ID)).thenReturn(context());
        when(fixture.engine.assess(any()))
                .thenThrow(new DomainException(ErrorCode.AI_TIMEOUT));

        fixture.service.scoreAsync(SESSION_ID);

        assertThat(fixture.session.getStatus())
                .isEqualTo(InterviewSessionStatus.SCORING_FAILED);
        assertThat(fixture.session.getScoringErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(fixture.session.getScoringErrorMessage())
                .isEqualTo(ErrorCode.AI_TIMEOUT.getDefaultMessage());
        verify(fixture.assessments, never()).save(any());
    }

    @Test
    void ignoresDuplicateWorkerAfterSessionWasClaimed() {
        InterviewSession session = scoringSession();
        session.markScoringStarted(NOW.minusSeconds(1));
        Fixture fixture = new Fixture(session);

        fixture.service.scoreAsync(SESSION_ID);

        verifyNoInteractions(fixture.contextLoader, fixture.engine, fixture.calculator);
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.SCORING);
    }

    private static InterviewSession scoringSession() {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .status(InterviewSessionStatus.SCORING)
                .languageCode("vi")
                .endReason(InterviewEndReason.AI_COMPLETED)
                .startedAt(NOW.minusSeconds(1200))
                .endedAt(NOW.minusSeconds(1))
                .createdAt(NOW.minusSeconds(1800))
                .updatedAt(NOW.minusSeconds(1))
                .build();
    }

    private static InterviewScoringContext context() {
        return new InterviewScoringContext(
                SESSION_ID,
                InterviewSessionStatus.SCORING,
                "vi",
                InterviewEndReason.AI_COMPLETED,
                1200,
                null,
                "Backend role",
                "Candidate discussed APIs",
                List.of(new InterviewScoringContext.FocusArea(
                        21L, "BACKEND", "Backend", "Spring",
                        InterviewFocusPriority.HIGH, "Required",
                        InterviewEvidenceStatus.SUFFICIENT, "REST evidence")),
                List.of(new InterviewScoringContext.Turn(
                        11L, 1, InterviewTurnRole.CANDIDATE,
                        "Tôi đã xây dựng REST API.", CandidateIntent.ANSWER,
                        null, null)));
    }

    private static InterviewAssessmentResult assessmentResult() {
        return new InterviewAssessmentResult(
                "Ứng viên có kiến thức backend.",
                "Nắm kiến thức chính nhưng cần giải thích trade-off.",
                List.of(new InterviewAssessmentResult.FocusAreaAssessment(
                        "BACKEND", 75,
                        InterviewEvidenceStatus.SUFFICIENT,
                        List.of(11L))),
                70,
                "Trình bày rõ ràng.",
                List.of("Bổ sung kết quả định lượng."));
    }

    private static InterviewScoreCalculation calculation() {
        return new InterviewScoreCalculation(
                new BigDecimal("75.00"),
                new BigDecimal("70.00"),
                new BigDecimal("74.00"),
                new BigDecimal("100.00"));
    }

    private static PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static final class Fixture {
        private final InterviewSession session;
        private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        private final InterviewAssessmentRepository assessments = mock(InterviewAssessmentRepository.class);
        private final InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        private final InterviewFocusAreaResultRepository focusAreaResults = mock(InterviewFocusAreaResultRepository.class);
        private final InterviewScoringContextLoader contextLoader = mock(InterviewScoringContextLoader.class);
        private final InterviewScoringEngine engine = mock(InterviewScoringEngine.class);
        private final InterviewScoreCalculator calculator = mock(InterviewScoreCalculator.class);
        private final InterviewSessionTransitionRecorder transitions = mock(InterviewSessionTransitionRecorder.class);
        private final InterviewFocusArea area;
        private final InterviewScoringServiceImpl service;

        private Fixture(InterviewSession session) {
            this.session = session;
            this.area = InterviewFocusArea.builder()
                    .id(21L)
                    .session(session)
                    .code("BACKEND")
                    .name("Backend")
                    .priority(InterviewFocusPriority.HIGH)
                    .reason("Required")
                    .plannedSeconds(300)
                    .evidenceStatus(InterviewEvidenceStatus.SUFFICIENT)
                    .displayOrder((short) 0)
                    .createdAt(NOW.minusSeconds(1000))
                    .updatedAt(NOW.minusSeconds(10))
                    .build();
            when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
            ChatModel chatModel = mock(ChatModel.class);
            when(chatModel.getOptions()).thenReturn(null);
            this.service = new InterviewScoringServiceImpl(
                    sessions,
                    assessments,
                    focusAreas,
                    focusAreaResults,
                    contextLoader,
                    engine,
                    calculator,
                    transitions,
                    new ObjectMapper(),
                    chatModel,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    mock(com.baseProject.myBaseProject.service.NotificationService.class),
                    transactionManager());
        }
    }
}
