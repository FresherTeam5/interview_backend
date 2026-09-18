package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.dto.session.InterviewAnswerResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.InterviewTurnResponse;
import com.baseProject.myBaseProject.dto.session.SubmitInterviewAnswerRequest;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.InterviewTurnInputMode;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewConversationEngine;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.model.InterviewTurnContext;
import com.baseProject.myBaseProject.interview.support.InterviewContextLoader;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.interview.support.InterviewSessionCloser;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import com.baseProject.myBaseProject.service.InterviewConversationService;
import com.baseProject.myBaseProject.util.IdempotencyKeyNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InterviewConversationServiceImpl implements InterviewConversationService {
    private static final Duration STALE_PROCESSING_AFTER = Duration.ofSeconds(60);

    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final InterviewFocusAreaRepository focusAreas;
    private final InterviewContextLoader contextLoader;
    private final InterviewConversationEngine engine;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final InterviewSessionCloser sessionCloser;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public InterviewConversationServiceImpl(
            InterviewSessionRepository sessions,
            InterviewTurnRepository turns,
            InterviewFocusAreaRepository focusAreas,
            InterviewContextLoader contextLoader,
            InterviewConversationEngine engine,
            InterviewSessionTransitionRecorder transitionRecorder,
            InterviewSessionCloser sessionCloser,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.turns = turns;
        this.focusAreas = focusAreas;
        this.contextLoader = contextLoader;
        this.engine = engine;
        this.transitionRecorder = transitionRecorder;
        this.sessionCloser = sessionCloser;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public InterviewConversationResponse start(Long userId, Long sessionId) {
        transactions.executeWithoutResult(status -> {
            InterviewSession session = ownedForUpdate(userId, sessionId);
            if (session.getStatus() == InterviewSessionStatus.IN_PROGRESS) {
                return;
            }
            if (session.getStatus() != InterviewSessionStatus.READY) {
                throw new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_STARTABLE);
            }

            Instant now = clock.instant();
            session.start(now);
            turns.save(InterviewTurn.builder()
                    .session(session)
                    .turnIndex(0)
                    .role(InterviewTurnRole.INTERVIEWER)
                    .contentText(session.getOpeningMessage().strip())
                    .action(InterviewTurnAction.OPENING)
                    .createdAt(now)
                    .build());

            transitionRecorder.record(
                    session,
                    InterviewSessionStatus.READY,
                    InterviewSessionStatus.IN_PROGRESS,
                    "User started interview",
                    InterviewTransitionActor.USER,
                    now);
        });

        return get(userId, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewConversationResponse get(Long userId, Long sessionId) {
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        return toConversationResponse(
                session,
                turns.findBySessionIdOrderByTurnIndexAsc(sessionId),
                clock.instant());
    }

    @Override
    public InterviewAnswerResponse answer(
            Long userId,
            Long sessionId,
            String rawIdempotencyKey,
            SubmitInterviewAnswerRequest request) {
        String idempotencyKey = IdempotencyKeyNormalizer.normalize(rawIdempotencyKey);
        String answer = request.answer().strip();
        InterviewTurnInputMode inputMode = requireTurnBasedInputMode(request.inputMode());

        // lưu câu trả lời vào db
        AnswerClaim claim = transactions.execute(status -> claimAnswer(
                userId, sessionId, idempotencyKey,
                request.expectedTurnIndex(), answer, inputMode));
        if (claim == null) {
            throw new IllegalStateException("Interview answer transaction returned no result");
        }
        if (claim.closedWithoutAnswer()) {
            return currentAnswerResponse(sessionId, null);
        }
        if (!claim.generateReply()) {
            return currentAnswerResponse(sessionId, claim.candidateTurnId());
        }

        try {
            InterviewContext context = contextLoader.loadInternal(sessionId);
            List<InterviewTurnContext> recentTurns = recentTurnContext(sessionId);
            InterviewReplyResult reply = engine.reply(
                    context, recentTurns, remainingSeconds(context.deadlineAt(), clock.instant()));

            persistReply(sessionId, claim.candidateTurnId(), reply);

            return currentAnswerResponse(sessionId, claim.candidateTurnId());
        } catch (DomainException exception) {
            markReplyFailed(claim.candidateTurnId(), exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            markReplyFailed(claim.candidateTurnId(), ErrorCode.AI_ERROR);
            throw exception;
        }
    }

    @Override
    public InterviewAnswerResponse retryFailedAnswer(
            Long userId,
            Long sessionId,
            Long candidateTurnId) {
        RetryAnswer retry = transactions.execute(status -> {
            InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new DomainException(
                            ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            InterviewTurn candidate = turns.findByIdAndSessionId(candidateTurnId, sessionId)
                    .orElseThrow(() -> new DomainException(
                            ErrorCode.INTERVIEW_TURN_NOT_RETRYABLE));
            if (candidate.getRole() != InterviewTurnRole.CANDIDATE
                    || candidate.getProcessingStatus() != InterviewTurnProcessingStatus.FAILED
                    || candidate.getIdempotencyKey() == null) {
                throw new DomainException(ErrorCode.INTERVIEW_TURN_NOT_RETRYABLE);
            }
            return new RetryAnswer(
                    candidate.getIdempotencyKey(),
                    new SubmitInterviewAnswerRequest(
                            candidate.getTurnIndex() - 1,
                            candidate.getContentText(),
                            candidate.getInputMode()));
        });
        if (retry == null) {
            throw new IllegalStateException("Interview retry transaction returned no result");
        }
        return answer(
                userId,
                sessionId,
                retry.idempotencyKey(),
                retry.request());
    }

    @Override
    public InterviewConversationResponse finish(Long userId, Long sessionId) {
        transactions.executeWithoutResult(status -> {
            InterviewSession session = ownedForUpdate(userId, sessionId);
            if (session.getStatus() == InterviewSessionStatus.SCORING
                    || session.getStatus() == InterviewSessionStatus.COMPLETED) {
                return;
            }
            if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
                throw new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_IN_PROGRESS);
            }
            sessionCloser.close(
                    session,
                    InterviewEndReason.CANDIDATE_FINISHED,
                    InterviewTransitionActor.USER,
                    clock.instant());
        });

        return get(userId, sessionId);
    }

    @Override
    public void continueAfterRealtimeFallback(Long userId, Long sessionId) {
        // Claim trong transaction để nhiều lệnh disconnect không cùng tạo phản hồi cho một turn.
        Long candidateTurnId = transactions.execute(status ->
                claimRealtimeFallbackCandidate(userId, sessionId));
        if (candidateTurnId == null) {
            return;
        }
        try {
            InterviewContext context = contextLoader.loadInternal(sessionId);
            List<InterviewTurnContext> recentTurns = recentTurnContext(sessionId);
            InterviewReplyResult reply = engine.reply(
                    context, recentTurns,
                    remainingSeconds(context.deadlineAt(), clock.instant()));
            persistReply(sessionId, candidateTurnId, reply);
        } catch (RuntimeException exception) {
            // Vẫn giữ hội thoại tiếp tục được khi AI không thể khôi phục câu trả lời cuối.
            persistFallbackRecoveryPrompt(sessionId, candidateTurnId);
        }
    }

    private Long claimRealtimeFallbackCandidate(Long userId, Long sessionId) {
        InterviewSession session = ownedForUpdate(userId, sessionId);
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
                || session.getMode() != InterviewSessionMode.TURN_BASED) {
            return null;
        }
        InterviewTurn current = turns
                .findBySessionIdAndTurnIndex(sessionId, session.getCurrentTurnIndex())
                .orElse(null);
        if (current == null
                || current.getRole() != InterviewTurnRole.CANDIDATE
                || current.getInputMode() != InterviewTurnInputMode.VOICE_REALTIME
                || turns.findByReplyToTurnId(current.getId()).isPresent()) {
            return null;
        }
        if (current.getProcessingStatus() == InterviewTurnProcessingStatus.PROCESSING) {
            throw new DomainException(ErrorCode.INTERVIEW_TURN_PROCESSING);
        }
        current.retryProcessing(clock.instant());

        return current.getId();
    }

    private void persistFallbackRecoveryPrompt(Long sessionId, Long candidateTurnId) {
        transactions.executeWithoutResult(status -> {
            // Kiểm tra lại dưới row lock vì trạng thái có thể đổi trong lúc chờ AI.
            InterviewSession session = sessions.findByIdForUpdate(sessionId)
                    .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            InterviewTurn candidate = turns.findById(candidateTurnId)
                    .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_TURN_OUT_OF_SEQUENCE));
            if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS
                    || session.getCurrentTurnIndex() != candidate.getTurnIndex()
                    || turns.findByReplyToTurnId(candidateTurnId).isPresent()) {
                return;
            }
            Instant now = clock.instant();
            turns.save(InterviewTurn.builder()
                    .session(session)
                    .replyToTurn(candidate)
                    .turnIndex(candidate.getTurnIndex() + 1)
                    .role(InterviewTurnRole.INTERVIEWER)
                    .contentText(fallbackRecoveryText(session.getLanguageCode()))
                    .action(InterviewTurnAction.HANDLE_REQUEST)
                    .createdAt(now)
                    .build());
            candidate.markCompleted();
            session.recordTurn(candidate.getTurnIndex() + 1, null, now);
        });
    }

    private String fallbackRecoveryText(String languageCode) {
        return switch (languageCode) {
            case "vi" -> "Kết nối realtime vừa bị gián đoạn. Bạn có thể nhắc lại ngắn gọn câu trả lời vừa rồi không?";
            case "ja" -> "リアルタイム接続が中断されました。先ほどの回答を短くもう一度お願いします。";
            default -> "The realtime connection was interrupted. Could you briefly repeat your last answer?";
        };
    }

    private AnswerClaim claimAnswer(
            Long userId,
            Long sessionId,
            String idempotencyKey,
            int expectedTurnIndex,
            String answer,
            InterviewTurnInputMode inputMode) {
        InterviewSession session = ownedForUpdate(userId, sessionId);
        Instant now = clock.instant();
        InterviewTurn existing = turns
                .findBySessionIdAndIdempotencyKey(sessionId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            // retry tránh tạo thêm câu trả lời mới
            requireSameAnswer(existing, expectedTurnIndex, answer, inputMode);

            return reclaimExisting(session, existing, now);
        }

        requireInProgress(session);
        requireTurnBasedSession(session);

        // check session deadline end chưa
        if (isDeadlineReached(session, now)) {
            sessionCloser.close(
                    session,
                    InterviewEndReason.TIME_EXPIRED,
                    InterviewTransitionActor.SYSTEM,
                    now);

            return new AnswerClaim(null, false, true);
        }
        if (session.getCurrentTurnIndex() != expectedTurnIndex) {
            throw new DomainException(ErrorCode.INTERVIEW_TURN_OUT_OF_SEQUENCE);
        }
        InterviewTurn current = turns
                .findBySessionIdAndTurnIndex(sessionId, expectedTurnIndex)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_TURN_OUT_OF_SEQUENCE));
        if (current.getRole() != InterviewTurnRole.INTERVIEWER
                || current.getAction() == InterviewTurnAction.CLOSE) {
            throw new DomainException(ErrorCode.INTERVIEW_TURN_OUT_OF_SEQUENCE);
        }

        InterviewTurn candidate = turns.save(InterviewTurn.builder()
                .session(session)
                .turnIndex(expectedTurnIndex + 1)
                .role(InterviewTurnRole.CANDIDATE)
                .inputMode(inputMode)
                .contentText(answer)
                .idempotencyKey(idempotencyKey)
                .processingStatus(InterviewTurnProcessingStatus.PROCESSING)
                .processingStartedAt(now)
                .createdAt(now)
                .build());
        session.recordTurn(candidate.getTurnIndex(), null, now);
        return new AnswerClaim(candidate.getId(), true, false);
    }

    private AnswerClaim reclaimExisting(
            InterviewSession session, InterviewTurn candidate, Instant now) {
        if (candidate.getProcessingStatus() == InterviewTurnProcessingStatus.COMPLETED) {
            return new AnswerClaim(candidate.getId(), false, false);
        }
        requireInProgress(session);
        if (isDeadlineReached(session, now)) {
            sessionCloser.close(
                    session,
                    InterviewEndReason.TIME_EXPIRED,
                    InterviewTransitionActor.SYSTEM,
                    now);
            return new AnswerClaim(candidate.getId(), false, false);
        }
        if (candidate.getProcessingStatus() == InterviewTurnProcessingStatus.PROCESSING
                && candidate.getProcessingStartedAt() != null
                && candidate.getProcessingStartedAt().plus(STALE_PROCESSING_AFTER).isAfter(now)) {
            // Chặn hai request đồng thời cùng gọi AI; job quá hạn mới được phép chạy lại.
            throw new DomainException(ErrorCode.INTERVIEW_TURN_PROCESSING);
        }
        candidate.retryProcessing(now);
        return new AnswerClaim(candidate.getId(), true, false);
    }

    private void requireSameAnswer(
            InterviewTurn existing,
            int expectedTurnIndex,
            String answer,
            InterviewTurnInputMode inputMode) {
        boolean same = existing.getTurnIndex() == expectedTurnIndex + 1
                && existing.getContentText().equals(answer)
                && existing.getInputMode() == inputMode;
        if (!same) {
            throw new DomainException(ErrorCode.INTERVIEW_TURN_IDEMPOTENCY_CONFLICT);
        }
    }

    private List<InterviewTurnContext> recentTurnContext(Long sessionId) {
        List<InterviewTurn> recent = new ArrayList<>(
                turns.findTop12BySessionIdOrderByTurnIndexDesc(sessionId));

        // Query lấy các lượt mới nhất theo DESC, prompt cần thứ tự hội thoại từ cũ đến mới.
        Collections.reverse(recent);

        return recent.stream().map(turn -> new InterviewTurnContext(
                turn.getTurnIndex(),
                turn.getRole(),
                turn.getContentText(),
                turn.getCandidateIntent(),
                turn.getAction(),
                turn.getFocusAreaCode())).toList();
    }

    private void persistReply(
            Long sessionId, Long candidateTurnId, InterviewReplyResult result) {
        transactions.executeWithoutResult(status -> {
            // Khóa lại session vì finish hoặc scheduler có thể chạy trong lúc chờ AI.
            InterviewSession session = sessions.findByIdForUpdate(sessionId)
                    .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            InterviewTurn candidate = turns.findById(candidateTurnId)
                    .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_TURN_OUT_OF_SEQUENCE));
            InterviewTurn existingReply = turns.findByReplyToTurnId(candidateTurnId).orElse(null);
            if (existingReply != null) {
                candidate.markCompleted();
                return;
            }
            if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
                return;
            }
            if (candidate.getProcessingStatus() != InterviewTurnProcessingStatus.PROCESSING
                    || session.getCurrentTurnIndex() != candidate.getTurnIndex()) {
                return;
            }

            Instant now = clock.instant();
            applyEvidenceUpdates(sessionId, result.evidenceUpdates(), now);
            // Phản hồi đến sau deadline không được giả thành lời nói của interviewer.
            if (isDeadlineReached(session, now)) {
                candidate.markCompleted(result.candidateIntent());
                session.recordTurn(candidate.getTurnIndex(), result.conversationSummary(), now);
                sessionCloser.close(
                        session,
                        InterviewEndReason.TIME_EXPIRED,
                        InterviewTransitionActor.SYSTEM,
                        now);
                return;
            }

            int replyIndex = candidate.getTurnIndex() + 1;
            turns.save(InterviewTurn.builder()
                    .session(session)
                    .replyToTurn(candidate)
                    .turnIndex(replyIndex)
                    .role(InterviewTurnRole.INTERVIEWER)
                    .contentText(result.interviewerMessage())
                    .action(result.action())
                    .focusAreaCode(result.action() == InterviewTurnAction.CLOSE
                            ? null : result.focusAreaCode())
                    .createdAt(now)
                    .build());
            candidate.markCompleted(result.candidateIntent());
            session.recordTurn(replyIndex, result.conversationSummary(), now);
            if (result.action() == InterviewTurnAction.CLOSE) {
                boolean candidateRequestedEnd = result.candidateIntent()
                        == CandidateIntent.REQUEST_END;
                sessionCloser.close(
                        session,
                        candidateRequestedEnd
                                ? InterviewEndReason.CANDIDATE_FINISHED
                                : InterviewEndReason.AI_COMPLETED,
                        candidateRequestedEnd
                                ? InterviewTransitionActor.USER
                                : InterviewTransitionActor.SYSTEM,
                        now);
            }
        });
    }

    private void applyEvidenceUpdates(
            Long sessionId,
            List<InterviewReplyResult.EvidenceUpdate> updates,
            Instant now) {
        Map<String, InterviewFocusArea> byCode = new HashMap<>();
        for (InterviewFocusArea area : focusAreas
                .findBySessionIdOrderByDisplayOrderAsc(sessionId)) {
            byCode.put(area.getCode(), area);
        }
        for (InterviewReplyResult.EvidenceUpdate update : updates) {
            InterviewFocusArea area = byCode.get(update.focusAreaCode());
            // Giữ evidence đơn điệu tăng ngay cả khi có kết quả AI đến muộn hoặc đồng thời.
            if (area != null && update.status().isAtLeast(area.getEvidenceStatus())) {
                area.updateEvidence(update.status(), update.evidenceSummary(), now);
            }
        }
    }

    private void markReplyFailed(Long candidateTurnId, ErrorCode errorCode) {
        // Transaction riêng giữ trạng thái FAILED dù request gọi AI kết thúc bằng exception.
        transactions.executeWithoutResult(status -> turns.findById(candidateTurnId)
                .filter(turn -> turn.getProcessingStatus()
                        == InterviewTurnProcessingStatus.PROCESSING)
                .ifPresent(turn -> turn.markFailed(errorCode.name())));
    }

    private InterviewAnswerResponse currentAnswerResponse(
            Long sessionId, Long candidateTurnId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        InterviewTurn candidate = candidateTurnId == null
                ? null : turns.findById(candidateTurnId).orElse(null);
        InterviewTurn interviewer = candidate == null
                ? turns.findBySessionIdAndTurnIndex(sessionId, session.getCurrentTurnIndex())
                        .orElse(null)
                : turns.findByReplyToTurnId(candidateTurnId).orElse(null);
        return new InterviewAnswerResponse(
                sessionId,
                session.getStatus(),
                session.getDeadlineAt(),
                session.getEndReason(),
                session.getEndedAt(),
                remainingSeconds(session, clock.instant()),
                session.getCurrentTurnIndex(),
                candidate == null ? null : toTurnResponse(candidate),
                interviewer == null ? null : toTurnResponse(interviewer));
    }

    private InterviewConversationResponse toConversationResponse(
            InterviewSession session, List<InterviewTurn> history, Instant now) {
        return new InterviewConversationResponse(
                session.getId(),
                session.getStatus(),
                session.getMode(),
                session.getRealtimeProvider(),
                session.getRealtimeVoiceName(),
                session.getStartedAt(),
                session.getDeadlineAt(),
                session.getEndReason(),
                session.getEndedAt(),
                remainingSeconds(session, now),
                session.getCurrentTurnIndex(),
                history.stream().map(this::toTurnResponse).toList());
    }

    private InterviewTurnResponse toTurnResponse(InterviewTurn turn) {
        return new InterviewTurnResponse(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getRole(),
                turn.getInputMode(),
                turn.getContentText(),
                turn.getCandidateIntent(),
                turn.getAction(),
                turn.getFocusAreaCode(),
                turn.getIdempotencyKey(),
                turn.getProcessingStatus(),
                turn.getProcessingErrorCode(),
                turn.isWasInterrupted(),
                turn.getLatencyMs(),
                turn.getCreatedAt());
    }

    private InterviewSession ownedForUpdate(Long userId, Long sessionId) {
        return sessions.findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private void requireInProgress(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_IN_PROGRESS);
        }
    }

    private void requireTurnBasedSession(InterviewSession session) {
        if (session.getMode() != InterviewSessionMode.TURN_BASED) {
            throw new DomainException(
                    ErrorCode.INTERVIEW_SESSION_MODE_MISMATCH,
                    "Answer endpoint requires a TURN_BASED session");
        }
    }

    private InterviewTurnInputMode requireTurnBasedInputMode(
            InterviewTurnInputMode inputMode) {
        if (inputMode != InterviewTurnInputMode.TEXT
                && inputMode != InterviewTurnInputMode.VOICE) {
            throw new DomainException(
                    ErrorCode.INTERVIEW_SESSION_OPTION_INVALID,
                    "Turn-based answers require TEXT or VOICE input mode");
        }
        return inputMode;
    }

    private boolean isDeadlineReached(InterviewSession session, Instant now) {
        return session.getDeadlineAt() != null && !session.getDeadlineAt().isAfter(now);
    }

    private long remainingSeconds(Instant deadline, Instant now) {
        if (deadline == null || !deadline.isAfter(now)) {
            return 0;
        }
        return Duration.between(now, deadline).getSeconds();
    }

    private long remainingSeconds(InterviewSession session, Instant now) {
        if (session.getStatus() == InterviewSessionStatus.READY) {
            return session.getDurationMinutes() * 60L;
        }
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            return 0;
        }
        return remainingSeconds(session.getDeadlineAt(), now);
    }

    private record AnswerClaim(
            Long candidateTurnId,
            boolean generateReply,
            boolean closedWithoutAnswer) {
    }

    private record RetryAnswer(
            String idempotencyKey,
            SubmitInterviewAnswerRequest request) {
    }
}
