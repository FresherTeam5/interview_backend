package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.dto.session.SubmitInterviewAnswerRequest;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnInputMode;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewConversationEngine;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.support.InterviewContextLoader;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.interview.support.InterviewSessionCloser;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewConversationServiceImplTest {
    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 501L;
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");

    @Test
    void startsReadySessionWithDurableOpeningTurnAndDeadline() {
        Fixture fixture = new Fixture(readySession());

        var response = fixture.service.start(USER_ID, SESSION_ID);

        assertThat(fixture.session.getStatus()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(fixture.session.getStartedAt()).isEqualTo(NOW);
        assertThat(fixture.session.getDeadlineAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(response.turns()).singleElement().satisfies(turn -> {
            assertThat(turn.turnIndex()).isZero();
            assertThat(turn.role()).isEqualTo(InterviewTurnRole.INTERVIEWER);
            assertThat(turn.action()).isEqualTo(InterviewTurnAction.OPENING);
            assertThat(turn.content()).isEqualTo("Xin chào, bạn hãy giới thiệu về mình.");
        });
        verify(fixture.transitions).record(
                fixture.session,
                InterviewSessionStatus.READY,
                InterviewSessionStatus.IN_PROGRESS,
                "User started interview",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.USER,
                NOW);
    }

    @Test
    void savesCandidateBeforeGeneratingAndPersistsAdaptiveReply() {
        InterviewSession session = inProgressSession();
        Fixture fixture = new Fixture(session);
        InterviewTurn opening = turn(
                1L, session, 0, InterviewTurnRole.INTERVIEWER,
                "Xin chào", InterviewTurnAction.OPENING);
        fixture.storedTurns.add(opening);
        InterviewFocusArea area = InterviewFocusArea.builder()
                .id(31L)
                .session(session)
                .code("BACKEND")
                .name("Backend")
                .description("Spring")
                .priority(InterviewFocusPriority.HIGH)
                .reason("Job critical")
                .plannedSeconds(300)
                .evidenceStatus(InterviewEvidenceStatus.NOT_EXPLORED)
                .displayOrder((short) 0)
                .createdAt(NOW.minusSeconds(30))
                .updatedAt(NOW.minusSeconds(30))
                .build();
        when(fixture.focusAreas.findBySessionIdOrderByDisplayOrderAsc(SESSION_ID))
                .thenReturn(List.of(area));
        when(fixture.contextLoader.loadInternal(SESSION_ID)).thenReturn(context());
        when(fixture.engine.reply(any(), any(), any(Long.class))).thenReturn(
                new InterviewReplyResult(
                        CandidateIntent.ANSWER,
                        InterviewTurnAction.FOLLOW_UP,
                        "Bạn đã đo kết quả của thay đổi đó như thế nào?",
                        "BACKEND",
                        "Ứng viên mô tả một quyết định backend nhưng chưa nêu kết quả.",
                        List.of(new InterviewReplyResult.EvidenceUpdate(
                                "BACKEND",
                                InterviewEvidenceStatus.PARTIAL,
                                "Có quyết định kỹ thuật, còn thiếu kết quả đo được."))));

        var response = fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Tôi đã tối ưu truy vấn SQL.", InterviewTurnInputMode.VOICE));

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.IN_PROGRESS);
        assertThat(response.candidateTurn().turnIndex()).isEqualTo(1);
        assertThat(response.candidateTurn().candidateIntent())
                .isEqualTo(CandidateIntent.ANSWER);
        assertThat(response.candidateTurn().inputMode())
                .isEqualTo(InterviewTurnInputMode.VOICE);
        assertThat(response.interviewerTurn().turnIndex()).isEqualTo(2);
        assertThat(response.interviewerTurn().action()).isEqualTo(
                InterviewTurnAction.FOLLOW_UP);
        assertThat(session.getCurrentTurnIndex()).isEqualTo(2);
        assertThat(session.getConversationSummary()).contains("quyết định backend");
        assertThat(area.getEvidenceStatus()).isEqualTo(InterviewEvidenceStatus.PARTIAL);
        assertThat(fixture.storedTurns).extracting(InterviewTurn::getRole)
                .containsExactly(
                        InterviewTurnRole.INTERVIEWER,
                        InterviewTurnRole.CANDIDATE,
                        InterviewTurnRole.INTERVIEWER);

        var retried = fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Tôi đã tối ưu truy vấn SQL.", InterviewTurnInputMode.VOICE));

        assertThat(retried.candidateTurn().id()).isEqualTo(response.candidateTurn().id());
        assertThat(retried.interviewerTurn().id()).isEqualTo(
                response.interviewerTurn().id());
        verify(fixture.engine, times(1)).reply(any(), any(), any(Long.class));
    }

    @Test
    void preservesFailedCandidateTurnSoClientCanRetryAfterReload() {
        InterviewSession session = inProgressSession();
        Fixture fixture = new Fixture(session);
        fixture.storedTurns.add(turn(
                1L, session, 0, InterviewTurnRole.INTERVIEWER,
                "Xin chào", InterviewTurnAction.OPENING));
        when(fixture.contextLoader.loadInternal(SESSION_ID)).thenReturn(context());
        when(fixture.engine.reply(any(), any(), any(Long.class)))
                .thenThrow(new DomainException(ErrorCode.AI_TIMEOUT));

        assertThatThrownBy(() -> fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Tôi đã xây dựng REST API.", InterviewTurnInputMode.TEXT)))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.AI_TIMEOUT));

        var conversation = fixture.service.get(USER_ID, SESSION_ID);
        assertThat(conversation.turns()).last().satisfies(turn -> {
            assertThat(turn.requestId()).isEqualTo("answer-1");
            assertThat(turn.candidateIntent()).isNull();
            assertThat(turn.processingStatus().name()).isEqualTo("FAILED");
            assertThat(turn.processingErrorCode()).isEqualTo("AI_TIMEOUT");
        });

        InterviewTurn failed = fixture.candidate.get();
        when(fixture.turns.findByIdAndSessionId(failed.getId(), SESSION_ID))
                .thenReturn(Optional.of(failed));
        org.mockito.Mockito.doReturn(
                new InterviewReplyResult(
                        CandidateIntent.ANSWER,
                        InterviewTurnAction.FOLLOW_UP,
                        "Bạn có thể mô tả kết quả không?",
                        null,
                        "Ứng viên đã mô tả REST API.",
                        List.of()))
                .when(fixture.engine).reply(any(), any(), any(Long.class));

        var retried = fixture.service.retryFailedAnswer(
                USER_ID, SESSION_ID, failed.getId());

        assertThat(retried.candidateTurn().id()).isEqualTo(failed.getId());
        assertThat(retried.candidateTurn().processingStatus().name()).isEqualTo("COMPLETED");
        assertThat(retried.interviewerTurn().content())
                .isEqualTo("Bạn có thể mô tả kết quả không?");
    }

    @Test
    void rejectsTurnBasedAnswerForRealtimeSession() {
        InterviewSession session = baseSession(InterviewSessionStatus.IN_PROGRESS)
                .mode(InterviewSessionMode.VOICE_REALTIME)
                .startedAt(NOW.minusSeconds(30))
                .deadlineAt(NOW.plusSeconds(1770))
                .build();
        Fixture fixture = new Fixture(session);

        assertThatThrownBy(() -> fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Câu trả lời", InterviewTurnInputMode.TEXT)))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_SESSION_MODE_MISMATCH));

        verifyNoInteractions(fixture.engine);
    }

    @Test
    void finishesWithoutAddingSyntheticInterviewerTurn() {
        InterviewSession session = inProgressSession();
        Fixture fixture = new Fixture(session);
        fixture.storedTurns.add(turn(
                1L, session, 0, InterviewTurnRole.INTERVIEWER,
                "Xin chào", InterviewTurnAction.OPENING));

        var response = fixture.service.finish(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.SCORING);
        assertThat(response.endReason()).isEqualTo(InterviewEndReason.CANDIDATE_FINISHED);
        assertThat(response.endedAt()).isEqualTo(NOW);
        assertThat(response.remainingSeconds()).isZero();
        assertThat(response.turns()).hasSize(1);
        verify(fixture.transitions).record(
                session,
                InterviewSessionStatus.IN_PROGRESS,
                InterviewSessionStatus.SCORING,
                "User ended interview",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.USER,
                NOW);
        verify(fixture.events).publishEvent(
                new com.baseProject.myBaseProject.interview.model.InterviewScoringRequestedEvent(
                        SESSION_ID));
    }

    @Test
    void expiresWithoutAddingSyntheticInterviewerTurn() {
        InterviewSession session = inProgressSession();
        session.setDeadlineAt(NOW);
        Fixture fixture = new Fixture(session);
        fixture.storedTurns.add(turn(
                1L, session, 0, InterviewTurnRole.INTERVIEWER,
                "Xin chào", InterviewTurnAction.OPENING));

        var response = fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Câu trả lời đến quá muộn.", InterviewTurnInputMode.TEXT));

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.SCORING);
        assertThat(response.endReason()).isEqualTo(InterviewEndReason.TIME_EXPIRED);
        assertThat(fixture.storedTurns).hasSize(1);
        verifyNoInteractions(fixture.engine);
    }

    @Test
    void persistsRealAiClosingMessage() {
        InterviewSession session = inProgressSession();
        Fixture fixture = new Fixture(session);
        fixture.storedTurns.add(turn(
                1L, session, 0, InterviewTurnRole.INTERVIEWER,
                "Xin chào", InterviewTurnAction.OPENING));
        when(fixture.contextLoader.loadInternal(SESSION_ID)).thenReturn(context());
        when(fixture.engine.reply(any(), any(), any(Long.class))).thenReturn(
                new InterviewReplyResult(
                        CandidateIntent.ANSWER,
                        InterviewTurnAction.CLOSE,
                        "Cảm ơn bạn đã chia sẻ. Buổi phỏng vấn kết thúc tại đây.",
                        null,
                        "Ứng viên đã hoàn thành cuộc phỏng vấn.",
                        List.of()));

        var response = fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Cảm ơn anh/chị.", InterviewTurnInputMode.TEXT));

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.SCORING);
        assertThat(response.endReason()).isEqualTo(InterviewEndReason.AI_COMPLETED);
        assertThat(response.candidateTurn().candidateIntent())
                .isEqualTo(CandidateIntent.ANSWER);
        assertThat(response.interviewerTurn().content())
                .isEqualTo("Cảm ơn bạn đã chia sẻ. Buổi phỏng vấn kết thúc tại đây.");
        assertThat(fixture.storedTurns).extracting(InterviewTurn::getRole)
                .containsExactly(
                        InterviewTurnRole.INTERVIEWER,
                        InterviewTurnRole.CANDIDATE,
                        InterviewTurnRole.INTERVIEWER);
    }

    @Test
    void attributesTypedEndRequestToCandidate() {
        InterviewSession session = inProgressSession();
        Fixture fixture = new Fixture(session);
        fixture.storedTurns.add(turn(
                1L, session, 0, InterviewTurnRole.INTERVIEWER,
                "Bạn hãy giới thiệu về mình.", InterviewTurnAction.OPENING));
        when(fixture.contextLoader.loadInternal(SESSION_ID)).thenReturn(context());
        when(fixture.engine.reply(any(), any(), any(Long.class))).thenReturn(
                new InterviewReplyResult(
                        CandidateIntent.REQUEST_END,
                        InterviewTurnAction.CLOSE,
                        "Cảm ơn bạn đã tham gia. Chúng ta sẽ kết thúc tại đây.",
                        null,
                        "Ứng viên chủ động yêu cầu kết thúc phỏng vấn.",
                        List.of()));

        var response = fixture.service.answer(
                USER_ID,
                SESSION_ID,
                "answer-1",
                new SubmitInterviewAnswerRequest(
                        0, "Tôi muốn dừng phỏng vấn.", InterviewTurnInputMode.TEXT));

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.SCORING);
        assertThat(response.endReason()).isEqualTo(InterviewEndReason.CANDIDATE_FINISHED);
        assertThat(response.candidateTurn().candidateIntent())
                .isEqualTo(CandidateIntent.REQUEST_END);
        verify(fixture.transitions).record(
                session,
                InterviewSessionStatus.IN_PROGRESS,
                InterviewSessionStatus.SCORING,
                "User ended interview",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.USER,
                NOW);
    }

    private static InterviewSession readySession() {
        return baseSession(InterviewSessionStatus.READY)
                .openingMessage("Xin chào, bạn hãy giới thiệu về mình.")
                .build();
    }

    private static InterviewSession inProgressSession() {
        return baseSession(InterviewSessionStatus.IN_PROGRESS)
                .openingMessage("Xin chào")
                .startedAt(NOW.minusSeconds(30))
                .deadlineAt(NOW.plusSeconds(1770))
                .lastActivityAt(NOW.minusSeconds(30))
                .currentTurnIndex(0)
                .build();
    }

    private static InterviewSession.InterviewSessionBuilder baseSession(
            InterviewSessionStatus status) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .status(status)
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .templateSnapshotJson("{}")
                .profileSnapshotJson("{}")
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60));
    }

    private static InterviewTurn turn(
            Long id,
            InterviewSession session,
            int index,
            InterviewTurnRole role,
            String content,
            InterviewTurnAction action) {
        return InterviewTurn.builder()
                .id(id)
                .session(session)
                .turnIndex(index)
                .role(role)
                .contentText(content)
                .action(action)
                .createdAt(NOW.minusSeconds(30))
                .build();
    }

    private static InterviewContext context() {
        return new InterviewContext(
                SESSION_ID,
                InterviewSessionStatus.IN_PROGRESS,
                "vi",
                30,
                InterviewerStyle.PROFESSIONAL,
                "Backend Java",
                "Candidate",
                "Xin chào",
                null,
                NOW.minusSeconds(30),
                NOW.plusSeconds(1770),
                1,
                List.of(new InterviewContext.FocusArea(
                        "BACKEND",
                        "Backend",
                        "Spring",
                        InterviewFocusPriority.HIGH,
                        "Job critical",
                        300,
                        InterviewEvidenceStatus.NOT_EXPLORED,
                        null,
                        (short) 0)));
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
        private final InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
        private final InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        private final InterviewContextLoader contextLoader = mock(InterviewContextLoader.class);
        private final InterviewConversationEngine engine = mock(InterviewConversationEngine.class);
        private final InterviewSessionTransitionRecorder transitions =
                mock(InterviewSessionTransitionRecorder.class);
        private final ApplicationEventPublisher events =
                mock(ApplicationEventPublisher.class);
        private final List<InterviewTurn> storedTurns = new ArrayList<>();
        private final AtomicReference<InterviewTurn> candidate = new AtomicReference<>();
        private final AtomicReference<InterviewTurn> interviewerReply = new AtomicReference<>();
        private final InterviewConversationServiceImpl service;

        private Fixture(InterviewSession session) {
            this.session = session;
            when(sessions.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                    .thenReturn(Optional.of(session));
            when(sessions.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
            when(sessions.findByIdAndUserId(SESSION_ID, USER_ID))
                    .thenReturn(Optional.of(session));
            when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(turns.findBySessionIdOrderByTurnIndexAsc(SESSION_ID))
                    .thenAnswer(invocation -> List.copyOf(storedTurns));
            when(turns.findBySessionIdAndIdempotencyKey(SESSION_ID, "answer-1"))
                    .thenAnswer(invocation -> Optional.ofNullable(candidate.get()));
            when(turns.findBySessionIdAndTurnIndex(any(), any(Integer.class)))
                    .thenAnswer(invocation -> storedTurns.stream()
                            .filter(turn -> turn.getTurnIndex()
                                    == invocation.getArgument(1, Integer.class))
                            .findFirst());
            when(turns.findTop12BySessionIdOrderByTurnIndexDesc(SESSION_ID))
                    .thenAnswer(invocation -> {
                        List<InterviewTurn> recent = new ArrayList<>(storedTurns);
                        Collections.reverse(recent);
                        return recent;
                    });
            when(turns.findById(any(Long.class))).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                return storedTurns.stream().filter(turn -> id.equals(turn.getId())).findFirst();
            });
            when(turns.findByReplyToTurnId(any(Long.class))).thenAnswer(invocation ->
                    Optional.ofNullable(interviewerReply.get()));
            when(turns.save(any())).thenAnswer(invocation -> {
                InterviewTurn turn = invocation.getArgument(0);
                if (turn.getId() == null) {
                    long id = turn.getRole() == InterviewTurnRole.CANDIDATE ? 10L : 11L;
                    ReflectionTestUtils.setField(turn, "id", id);
                }
                storedTurns.add(turn);
                if (turn.getRole() == InterviewTurnRole.CANDIDATE) {
                    candidate.set(turn);
                } else if (turn.getAction() != InterviewTurnAction.OPENING) {
                    interviewerReply.set(turn);
                }
                return turn;
            });

            service = new InterviewConversationServiceImpl(
                    sessions,
                    turns,
                    focusAreas,
                    contextLoader,
                    engine,
                    transitions,
                    new InterviewSessionCloser(turns, transitions, events),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    transactionManager());
        }
    }
}
