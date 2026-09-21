package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionTransitionResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.dto.admin.AdminBulkSessionRequest;
import com.baseProject.myBaseProject.dto.admin.AdminBulkSessionResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionActionRequest;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDiagnosticsResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionTransitionRepository;
import com.baseProject.myBaseProject.service.AdminInterviewSessionService;
import com.baseProject.myBaseProject.service.InterviewReportService;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.SystemSettingService;
import com.baseProject.myBaseProject.interview.support.InterviewSessionCloser;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class AdminInterviewSessionServiceImpl implements AdminInterviewSessionService {
    private final InterviewSessionRepository sessions;
    private final InterviewSessionTransitionRepository transitions;
    private final InterviewSessionService interviewSessionService;
    private final InterviewReportService interviewReportService;
    private final InterviewSessionCloser sessionCloser;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final AdminAuditService audit;
    private final Clock clock;

    @Autowired(required = false)
    private SystemSettingService systemSettings;

    @Autowired
    public AdminInterviewSessionServiceImpl(
            InterviewSessionRepository sessions,
            InterviewSessionTransitionRepository transitions,
            InterviewSessionService interviewSessionService,
            InterviewReportService interviewReportService,
            InterviewSessionCloser sessionCloser,
            InterviewSessionTransitionRecorder transitionRecorder,
            AdminAuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.transitions = transitions;
        this.interviewSessionService = interviewSessionService;
        this.interviewReportService = interviewReportService;
        this.sessionCloser = sessionCloser;
        this.transitionRecorder = transitionRecorder;
        this.audit = audit;
        this.clock = clock;
    }

    // Giữ constructor cũ cho unit test hiện có.
    public AdminInterviewSessionServiceImpl(
            InterviewSessionRepository sessions,
            InterviewSessionTransitionRepository transitions,
            InterviewSessionService interviewSessionService,
            InterviewReportService interviewReportService) {
        this(sessions, transitions, interviewSessionService, interviewReportService,
                null, null, null, Clock.systemUTC());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPageResponse<AdminSessionSummaryResponse> list(
            String keyword,
            InterviewSessionStatus status,
            InterviewSessionMode mode,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "createdFrom must be before or equal to createdTo");
        }
        Page<InterviewSession> result = sessions.searchForAdmin(
                normalizeKeyword(keyword),
                status,
                mode,
                createdFrom,
                createdTo,
                pageRequest(page, size));
        return new AdminPageResponse<>(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSessionDetailResponse get(Long sessionId) {
        InterviewSession session = sessions.findByIdForAdmin(sessionId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        List<AdminSessionTransitionResponse> history = transitions
                .findBySessionIdOrderByOccurredAtAsc(sessionId)
                .stream()
                .map(this::toTransition)
                .toList();
        return toDetail(session, history);
    }

    @Override
    @Transactional
    public AdminSessionDetailResponse retryPreparation(Long adminId, Long sessionId) {
        interviewSessionService.retryPreparationForAdmin(sessionId);
        record(adminId, AdminAuditAction.INTERVIEW_PREPARATION_RETRIED,
                sessionId, Map.of("status", InterviewSessionStatus.PREPARING));
        log.info("Admin {} retried interview preparation for session {}", adminId, sessionId);
        return get(sessionId);
    }

    @Override
    @Transactional
    public AdminSessionDetailResponse retryScoring(Long adminId, Long sessionId) {
        interviewReportService.retryScoringForAdmin(sessionId);
        record(adminId, AdminAuditAction.INTERVIEW_SCORING_RETRIED,
                sessionId, Map.of("status", InterviewSessionStatus.SCORING));
        log.info("Admin {} retried interview scoring for session {}", adminId, sessionId);
        return get(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPageResponse<AdminSessionSummaryResponse> listStale(
            InterviewSessionStatus status, int staleMinutes, int page, int size) {
        if (!EnumSet.of(InterviewSessionStatus.PREPARING, InterviewSessionStatus.IN_PROGRESS,
                InterviewSessionStatus.SCORING).contains(status)
                || staleMinutes < 1 || staleMinutes > 10_080) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "status must be PREPARING, IN_PROGRESS or SCORING and staleMinutes 1..10080");
        }
        Page<InterviewSession> result = sessions.findStaleForAdmin(status,
                clock.instant().minus(staleMinutes, ChronoUnit.MINUTES), pageRequest(page, size));
        return new AdminPageResponse<>(result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSessionDiagnosticsResponse diagnostics(Long sessionId) {
        InterviewSession session = sessions.findByIdForAdmin(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        return new AdminSessionDiagnosticsResponse(session.getId(), session.getStatus(),
                session.getPreparationErrorCode(), sanitize(session.getPreparationErrorMessage()),
                session.getScoringErrorCode(), sanitize(session.getScoringErrorMessage()),
                session.getPlanModelName(), session.getPlanPromptVersion(),
                session.getRealtimeProvider(), session.getCurrentTurnIndex(),
                transitions.countBySessionIdAndActorAndToStatus(sessionId,
                        InterviewTransitionActor.ADMIN, InterviewSessionStatus.PREPARING),
                transitions.countBySessionIdAndActorAndToStatus(sessionId,
                        InterviewTransitionActor.ADMIN, InterviewSessionStatus.SCORING),
                session.getLastActivityAt(), session.getUpdatedAt());
    }

    @Override
    @Transactional
    public AdminSessionDetailResponse forceClose(
            Long adminId, Long sessionId, AdminSessionActionRequest request) {
        InterviewSession session = locked(sessionId);
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new DomainException(ErrorCode.INTERVIEW_ADMIN_OPERATION_INVALID);
        }
        sessionCloser.close(session, InterviewEndReason.SYSTEM_TERMINATED,
                InterviewTransitionActor.ADMIN, clock.instant());
        record(adminId, AdminAuditAction.INTERVIEW_FORCE_CLOSED, sessionId,
                Map.of("reason", reason(request, "Admin force-closed interview"),
                        "nextStatus", InterviewSessionStatus.SCORING));
        return get(sessionId);
    }

    @Override
    @Transactional
    public AdminSessionDetailResponse terminate(
            Long adminId, Long sessionId, AdminSessionActionRequest request) {
        InterviewSession session = locked(sessionId);
        InterviewSessionStatus previous = session.getStatus();
        if (!EnumSet.of(InterviewSessionStatus.PREPARING, InterviewSessionStatus.READY,
                InterviewSessionStatus.PREPARATION_FAILED, InterviewSessionStatus.IN_PROGRESS,
                InterviewSessionStatus.SCORING, InterviewSessionStatus.SCORING_FAILED)
                .contains(previous)) {
            throw new DomainException(ErrorCode.INTERVIEW_ADMIN_OPERATION_INVALID);
        }
        Instant now = clock.instant();
        String reason = reason(request, "Admin terminated interview session");
        session.terminateByAdmin(now);
        transitionRecorder.record(session, previous, InterviewSessionStatus.CANCELLED,
                reason, InterviewTransitionActor.ADMIN, now);
        record(adminId, AdminAuditAction.INTERVIEW_TERMINATED, sessionId,
                Map.of("previousStatus", previous, "reason", reason));
        return get(sessionId);
    }

    @Override
    public AdminBulkSessionResponse bulkRetryPreparation(
            Long adminId, AdminBulkSessionRequest request) {
        return bulkRetry(adminId, request, true);
    }

    @Override
    public AdminBulkSessionResponse bulkRetryScoring(
            Long adminId, AdminBulkSessionRequest request) {
        return bulkRetry(adminId, request, false);
    }

    private AdminBulkSessionResponse bulkRetry(
            Long adminId, AdminBulkSessionRequest request, boolean preparation) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(request.sessionIds());
        int limit = systemSettings == null ? 25 : systemSettings.integerValue(
                SystemSettingServiceImpl.ADMIN_BULK_RETRY_LIMIT, 25);
        if (ids.contains(null) || ids.size() > limit) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "sessionIds must contain at most " + limit + " unique non-null values");
        }
        List<AdminBulkSessionResponse.Item> items = new ArrayList<>();
        for (Long id : ids) {
            try {
                AdminSessionDetailResponse result = preparation
                        ? retryPreparation(adminId, id) : retryScoring(adminId, id);
                items.add(new AdminBulkSessionResponse.Item(
                        id, true, result.status(), null, null));
            } catch (DomainException exception) {
                items.add(new AdminBulkSessionResponse.Item(id, false, currentStatus(id),
                        exception.getCode().name(), exception.getMessage()));
            }
        }
        int succeeded = (int) items.stream().filter(AdminBulkSessionResponse.Item::success).count();
        return new AdminBulkSessionResponse(ids.size(), succeeded,
                ids.size() - succeeded, items);
    }

    private InterviewSessionStatus currentStatus(Long sessionId) {
        return sessions.findByIdForAdmin(sessionId).map(InterviewSession::getStatus).orElse(null);
    }

    private InterviewSession locked(Long sessionId) {
        return sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private String reason(AdminSessionActionRequest request, String fallback) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return fallback;
        }
        return request.reason().strip();
    }

    private String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String sanitized = message.replaceAll("(?i)(api[_-]?key|token|secret)=[^\\s,;]+", "$1=[REDACTED]");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }

    private void record(Long adminId, AdminAuditAction action, Long sessionId, Object after) {
        if (audit != null) {
            audit.record(adminId, action, "INTERVIEW_SESSION", sessionId, null, after);
        }
    }

    private AdminSessionSummaryResponse toSummary(InterviewSession session) {
        return new AdminSessionSummaryResponse(
                session.getId(),
                toUser(session.getUser()),
                session.getStatus(),
                session.getTemplateTitleSnapshot(),
                session.getProfileNameSnapshot(),
                session.getMode(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getPreparationErrorCode(),
                session.getScoringErrorCode(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private AdminSessionDetailResponse toDetail(
            InterviewSession session,
            List<AdminSessionTransitionResponse> history) {
        return new AdminSessionDetailResponse(
                session.getId(),
                toUser(session.getUser()),
                session.getStatus(),
                session.getTemplateTitleSnapshot(),
                session.getProfileNameSnapshot(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getMode(),
                session.getRealtimeProvider(),
                session.getRealtimeVoiceName(),
                session.getCurrentTurnIndex(),
                session.getPreparationErrorCode(),
                sanitize(session.getPreparationErrorMessage()),
                session.getPlanSchemaVersion(),
                session.getPlanModelName(),
                session.getPlanPromptVersion(),
                session.getScoringErrorCode(),
                sanitize(session.getScoringErrorMessage()),
                session.getPreparationStartedAt(),
                session.getPreparedAt(),
                session.getStartedAt(),
                session.getDeadlineAt(),
                session.getLastActivityAt(),
                session.getEndReason(),
                session.getEndedAt(),
                session.getScoringStartedAt(),
                session.getCompletedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                history);
    }

    private AdminSessionTransitionResponse toTransition(
            InterviewSessionTransition transition) {
        return new AdminSessionTransitionResponse(
                transition.getId(),
                transition.getFromStatus(),
                transition.getToStatus(),
                transition.getReason(),
                transition.getActor(),
                transition.getOccurredAt());
    }

    private AdminUserReferenceResponse toUser(UserAccount user) {
        return new AdminUserReferenceResponse(
                user.getId(), user.getFullName(), user.getEmail());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
