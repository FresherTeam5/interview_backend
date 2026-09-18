package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.InterviewSessionProperties;
import com.baseProject.myBaseProject.config.properites.RealtimeProperties;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewOptionResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewPreparationService;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.interview.support.InterviewSnapshotFactory;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import com.baseProject.myBaseProject.util.IdempotencyKeyNormalizer;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class InterviewSessionServiceImpl implements InterviewSessionService {
    private final InterviewSessionRepository sessions;
    private final InterviewTemplateRepository templates;
    private final CandidateProfileRepository profiles;
    private final UserAccountRepository users;
    private final InterviewFocusAreaRepository focusAreas;
    private final InterviewSnapshotFactory snapshotFactory;
    private final InterviewPreparationService preparationService;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final InterviewSessionMapper mapper;
    private final InterviewSessionProperties properties;
    private final RealtimeProperties realtimeProperties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public InterviewSessionServiceImpl(
            InterviewSessionRepository sessions,
            InterviewTemplateRepository templates,
            CandidateProfileRepository profiles,
            UserAccountRepository users,
            InterviewFocusAreaRepository focusAreas,
            InterviewSnapshotFactory snapshotFactory,
            InterviewPreparationService preparationService,
            InterviewSessionTransitionRecorder transitionRecorder,
            InterviewSessionMapper mapper,
            InterviewSessionProperties properties,
            RealtimeProperties realtimeProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.templates = templates;
        this.profiles = profiles;
        this.users = users;
        this.focusAreas = focusAreas;
        this.snapshotFactory = snapshotFactory;
        this.preparationService = preparationService;
        this.transitionRecorder = transitionRecorder;
        this.mapper = mapper;
        this.properties = properties;
        this.realtimeProperties = realtimeProperties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public InterviewSessionOptionsResponse options() {
        List<InterviewOptionResponse> languages = properties.supportedLanguages().stream()
                .map(code -> new InterviewOptionResponse(code, languageName(code)))
                .toList();
        List<InterviewOptionResponse> styles = Arrays.stream(InterviewerStyle.values())
                .map(style -> new InterviewOptionResponse(style.name(), styleName(style)))
                .toList();
        List<InterviewOptionResponse> modes = properties.supportedModes().stream()
                .filter(this::isModeAvailable)
                .map(mode -> new InterviewOptionResponse(mode.name(), modeName(mode)))
                .toList();

        return new InterviewSessionOptionsResponse(
                languages, properties.supportedDurations(), styles, modes);
    }

    @Override
    public InterviewSessionStatusResponse create(
            Long userId, String rawIdempotencyKey, CreateInterviewSessionRequest request) {
        String idempotencyKey = IdempotencyKeyNormalizer.normalize(rawIdempotencyKey);
        String languageCode = normalizeLanguage(request.languageCode());
        int durationMinutes = requireDuration(request.durationMinutes());
        InterviewSessionMode mode = requireMode(request.mode());

        CreationResult result;

        // Unique constraint xử lý race khi hai request cùng idempotency key đồng thời tạo session.
        try {
            result = transactions.execute(status -> createInTransaction(
                    userId, idempotencyKey, request, languageCode, durationMinutes, mode));
        } catch (DataIntegrityViolationException exception) {
            InterviewSession existing = sessions
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> exception);
            requireSameRequest(existing, request, languageCode, durationMinutes, mode);
            result = new CreationResult(existing.getId(), false);
        }
        if (result == null) {
            throw new IllegalStateException("Interview session transaction returned no result");
        }
        if (result.dispatchPreparation()) {

            // tạo việc
            submitPreparation(result.sessionId());
        }

        return get(userId, result.sessionId());
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewSessionStatusResponse get(Long userId, Long sessionId) {
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        return mapper.toStatusResponse(session);
    }

    @Override
    public InterviewSessionStatusResponse retryPreparation(Long userId, Long sessionId) {
        retryPreparation(
                userId,
                sessionId,
                InterviewTransitionActor.USER,
                "User retried interview preparation");
        return get(userId, sessionId);
    }

    @Override
    public void retryPreparationForAdmin(Long sessionId) {
        retryPreparation(
                null,
                sessionId,
                InterviewTransitionActor.ADMIN,
                "Admin retried interview preparation");
    }

    @Override
    public InterviewSessionStatusResponse cancel(Long userId, Long sessionId) {
        transactions.executeWithoutResult(status -> {
            InterviewSession session = sessions.findOwnedByIdForUpdate(sessionId, userId)
                    .orElseThrow(() -> new DomainException(
                            ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            InterviewSessionStatus previous = session.getStatus();
            if (previous == InterviewSessionStatus.CANCELLED) {
                return;
            }
            if (previous != InterviewSessionStatus.PREPARING
                    && previous != InterviewSessionStatus.READY
                    && previous != InterviewSessionStatus.PREPARATION_FAILED) {
                throw new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_CANCELLABLE);
            }
            Instant now = clock.instant();
            session.cancel(now);
            transitionRecorder.record(
                    session,
                    previous,
                    InterviewSessionStatus.CANCELLED,
                    "User cancelled interview before it started",
                    InterviewTransitionActor.USER,
                    now);
        });
        return get(userId, sessionId);
    }

    private void retryPreparation(
            Long ownerId,
            Long sessionId,
            InterviewTransitionActor actor,
            String reason) {
        transactions.executeWithoutResult(status -> {

            // Khóa session để các request retry đồng thời không cùng khởi tạo lại kế hoạch.
            InterviewSession session = (ownerId == null
                    ? sessions.findByIdForUpdate(sessionId)
                    : sessions.findOwnedByIdForUpdate(sessionId, ownerId))
                    .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            if (session.getStatus() != InterviewSessionStatus.PREPARATION_FAILED) {
                throw new DomainException(
                        ErrorCode.INTERVIEW_SESSION_PREPARATION_NOT_RETRYABLE);
            }
            Instant now = clock.instant();

            // Xóa kế hoạch dở dang để lần chuẩn bị lại tạo một tập focus area nhất quán.
            focusAreas.deleteBySessionId(sessionId);
            session.retryPreparation(now);
            transitionRecorder.record(
                    session, InterviewSessionStatus.PREPARATION_FAILED,
                    InterviewSessionStatus.PREPARING,
                    reason,
                    actor, now);
        });
        submitPreparation(sessionId);
    }

    // helper
    private CreationResult createInTransaction(
            Long userId,
            String idempotencyKey,
            CreateInterviewSessionRequest request,
            String languageCode,
            int durationMinutes,
            InterviewSessionMode mode) {
        InterviewSession existing = sessions
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, request, languageCode, durationMinutes, mode);

            return new CreationResult(existing.getId(), false);
        }

        InterviewTemplate template = templates
                .findAccessibleForSession(request.templateId(), userId)
                .orElseThrow(() -> new DomainException(ErrorCode.TEMPLATE_NOT_FOUND));
        if (template.getArchivedAt() != null) {
            throw new DomainException(ErrorCode.TEMPLATE_ARCHIVED);
        }
        if (!template.isConfirmed()) {
            throw new DomainException(ErrorCode.TEMPLATE_CONFIRM_REQUIRED);
        }

        CandidateProfile profile = profiles
                .findAvailableByIdAndUserId(request.profileId(), userId)
                .orElseThrow(() -> new DomainException(ErrorCode.PROFILE_NOT_FOUND));
        if (!profile.isConfirmed()) {
            throw new DomainException(ErrorCode.PROFILE_CONFIRM_REQUIRED);
        }

        // Snapshot giữ ngữ cảnh phỏng vấn ổn định nếu template hoặc profile được sửa về sau.
        InterviewSnapshotFactory.SnapshotBundle snapshots =
                snapshotFactory.create(template, profile);
        Instant now = clock.instant();
        InterviewSession session = sessions.save(InterviewSession.builder()
                .user(users.getReferenceById(userId))
                .template(template)
                .profile(profile)
                .idempotencyKey(idempotencyKey)
                .templateTitleSnapshot(template.getTitle())
                .profileNameSnapshot(profile.getName())
                .status(InterviewSessionStatus.PREPARING)
                .mode(mode)
                .languageCode(languageCode)
                .durationMinutes(durationMinutes)
                .interviewerStyle(request.interviewerStyle())
                .templateSnapshotJson(snapshots.templateJson())
                .profileSnapshotJson(snapshots.profileJson())
                .createdAt(now)
                .updatedAt(now)
                .build());
        transitionRecorder.record(
                session, null, InterviewSessionStatus.PREPARING,
                "Interview session created", InterviewTransitionActor.USER, now);

        return new CreationResult(session.getId(), true);
    }

    private void submitPreparation(Long sessionId) {
        try {
            preparationService.prepareAsync(sessionId);
        } catch (TaskRejectedException exception) {

            // Phản ánh lỗi hàng đợi vào session để người dùng có thể chủ động retry
            markDispatchFailed(sessionId);
        }
    }

    private void markDispatchFailed(Long sessionId) {
        transactions.executeWithoutResult(status -> {
            InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || session.getStatus() != InterviewSessionStatus.PREPARING) {
                return;
            }
            Instant now = clock.instant();
            session.markPreparationFailed(
                    ErrorCode.AI_SERVICE_UNAVAILABLE.name(),
                    Message.AI_SERVICE_UNAVAILABLE, now);
            transitionRecorder.record(
                    session, InterviewSessionStatus.PREPARING,
                    InterviewSessionStatus.PREPARATION_FAILED,
                    "Interview preparation queue was full",
                    InterviewTransitionActor.SYSTEM, now);
        });
    }

    private void requireSameRequest(
            InterviewSession existing,
            CreateInterviewSessionRequest request,
            String languageCode,
            int durationMinutes,
            InterviewSessionMode mode) {

        // Một idempotency key chỉ được đại diện cho duy nhất một nội dung request
        boolean same = existing.getTemplate().getId().equals(request.templateId())
                && existing.getProfile().getId().equals(request.profileId())
                && existing.getLanguageCode().equals(languageCode)
                && existing.getDurationMinutes() == durationMinutes
                && existing.getInterviewerStyle() == request.interviewerStyle()
                && existing.getMode() == mode;
        if (!same) {
            throw new DomainException(ErrorCode.INTERVIEW_SESSION_IDEMPOTENCY_CONFLICT);
        }
    }

    private String normalizeLanguage(String rawValue) {
        if (rawValue == null) {
            throw new DomainException(ErrorCode.INTERVIEW_SESSION_OPTION_INVALID);
        }
        String requested = rawValue.strip();
        return properties.supportedLanguages().stream()
                .filter(value -> value.equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_SESSION_OPTION_INVALID,
                        "Unsupported interview language: " + requested));
    }

    private int requireDuration(Integer requested) {
        if (requested == null || !properties.supportedDurations().contains(requested)) {
            throw new DomainException(
                    ErrorCode.INTERVIEW_SESSION_OPTION_INVALID,
                    "Unsupported interview duration: " + requested);
        }
        return requested;
    }

    private InterviewSessionMode requireMode(InterviewSessionMode requested) {
        InterviewSessionMode mode = requested == null
                ? InterviewSessionMode.TURN_BASED
                : requested;
        if (!properties.supportedModes().contains(mode) || !isModeAvailable(mode)) {
            throw new DomainException(
                    ErrorCode.INTERVIEW_SESSION_OPTION_INVALID,
                    "Unsupported interview mode: " + mode);
        }
        return mode;
    }

    private boolean isModeAvailable(InterviewSessionMode mode) {
        // Dùng chung quy tắc để option hiển thị và request tạo session không bị lệch nhau.
        return mode != InterviewSessionMode.VOICE_REALTIME || realtimeProperties.enabled();
    }

    private String languageName(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "vi" -> "Tiếng Việt";
            case "en" -> "English";
            case "ja" -> "日本語";
            default -> code;
        };
    }

    private String styleName(InterviewerStyle style) {
        return switch (style) {
            case FRIENDLY -> "Thân thiện";
            case PROFESSIONAL -> "Chuyên nghiệp";
            case CHALLENGING -> "Thử thách";
        };
    }

    private String modeName(InterviewSessionMode mode) {
        return switch (mode) {
            case TURN_BASED -> "Theo lượt";
            case VOICE_REALTIME -> "Giọng nói thời gian thực";
        };
    }

    private record CreationResult(Long sessionId, boolean dispatchPreparation) {
    }
}
