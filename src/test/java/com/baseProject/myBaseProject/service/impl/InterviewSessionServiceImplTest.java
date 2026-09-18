package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.InterviewSessionProperties;
import com.baseProject.myBaseProject.config.properites.RealtimeProperties;
import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewOptionResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
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
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSessionServiceImplTest {
    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");

    @Test
    void createsImmutableSnapshotsAndDispatchesPreparation() {
        Fixture fixture = new Fixture();
        fixture.stubNewSession();

        var response = fixture.service.create(
                USER_ID, "request-1", request(InterviewerStyle.PROFESSIONAL));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.PREPARING);
        assertThat(response.mode()).isEqualTo(InterviewSessionMode.TURN_BASED);
        assertThat(response.templateTitle()).isEqualTo("Backend Java");
        assertThat(response.profileName()).isEqualTo("Minh profile");
        assertThat(fixture.saved.get().getTemplateSnapshotJson()).isEqualTo("{\"template\":1}");
        assertThat(fixture.saved.get().getProfileSnapshotJson()).isEqualTo("{\"profile\":2}");
        verify(fixture.preparationService).prepareAsync(501L);
    }

    @Test
    void exposesOnlyTurnBasedAndRealtimeExecutionModes() {
        Fixture fixture = new Fixture();

        assertThat(fixture.service.options().modes())
                .extracting(InterviewOptionResponse::code)
                .containsExactly("TURN_BASED", "VOICE_REALTIME");
    }

    @Test
    void repeatedIdempotentRequestReturnsExistingSessionWithoutDispatchingAgain() {
        Fixture fixture = new Fixture();
        InterviewSession existing = fixture.session(501L, InterviewSessionStatus.READY);
        when(fixture.sessions.findByUserIdAndIdempotencyKey(USER_ID, "request-1"))
                .thenReturn(Optional.of(existing));
        when(fixture.sessions.findByIdAndUserId(501L, USER_ID)).thenReturn(Optional.of(existing));

        var response = fixture.service.create(
                USER_ID, "request-1", request(InterviewerStyle.PROFESSIONAL));

        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.READY);
        verify(fixture.preparationService, never()).prepareAsync(any());
        verify(fixture.snapshotFactory, never()).create(any(), any());
    }

    @Test
    void rejectsReusingIdempotencyKeyWithDifferentOptions() {
        Fixture fixture = new Fixture();
        InterviewSession existing = fixture.session(501L, InterviewSessionStatus.PREPARING);
        when(fixture.sessions.findByUserIdAndIdempotencyKey(USER_ID, "request-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> fixture.service.create(
                USER_ID, "request-1", request(InterviewerStyle.FRIENDLY)))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_SESSION_IDEMPOTENCY_CONFLICT));
    }

    @Test
    void rejectsUnconfirmedProfileBeforeTakingSnapshots() {
        Fixture fixture = new Fixture();
        fixture.profile.setConfirmedAt(null);
        when(fixture.templates.findAccessibleForSession(101L, USER_ID))
                .thenReturn(Optional.of(fixture.template));
        when(fixture.profiles.findAvailableByIdAndUserId(35L, USER_ID))
                .thenReturn(Optional.of(fixture.profile));

        assertThatThrownBy(() -> fixture.service.create(
                USER_ID, "request-1", request(InterviewerStyle.PROFESSIONAL)))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.PROFILE_CONFIRM_REQUIRED));
        verify(fixture.snapshotFactory, never()).create(any(), any());
    }

    @Test
    void adminRetriesPreparationWithoutImpersonatingSessionOwner() {
        Fixture fixture = new Fixture();
        InterviewSession session = fixture.session(
                501L, InterviewSessionStatus.PREPARATION_FAILED);
        when(fixture.sessions.findByIdForUpdate(501L)).thenReturn(Optional.of(session));

        fixture.service.retryPreparationForAdmin(501L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.PREPARING);
        verify(fixture.focusAreas).deleteBySessionId(501L);
        verify(fixture.preparationService).prepareAsync(501L);
        verify(fixture.transitionRecorder).record(
                session,
                InterviewSessionStatus.PREPARATION_FAILED,
                InterviewSessionStatus.PREPARING,
                "Admin retried interview preparation",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.ADMIN,
                NOW);
    }

    @Test
    void cancelsReadySessionIdempotentlyBeforeItStarts() {
        Fixture fixture = new Fixture();
        InterviewSession session = fixture.session(501L, InterviewSessionStatus.READY);
        when(fixture.sessions.findOwnedByIdForUpdate(501L, USER_ID))
                .thenReturn(Optional.of(session));
        when(fixture.sessions.findByIdAndUserId(501L, USER_ID))
                .thenReturn(Optional.of(session));

        var response = fixture.service.cancel(USER_ID, 501L);

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.CANCELLED);
        assertThat(session.getEndReason())
                .isEqualTo(com.baseProject.myBaseProject.enums.InterviewEndReason.USER_CANCELLED);
        assertThat(session.getEndedAt()).isEqualTo(NOW);
        verify(fixture.transitionRecorder).record(
                session,
                InterviewSessionStatus.READY,
                InterviewSessionStatus.CANCELLED,
                "User cancelled interview before it started",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.USER,
                NOW);

        fixture.service.cancel(USER_ID, 501L);
        verify(fixture.transitionRecorder).record(
                session,
                InterviewSessionStatus.READY,
                InterviewSessionStatus.CANCELLED,
                "User cancelled interview before it started",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.USER,
                NOW);
    }

    private static CreateInterviewSessionRequest request(InterviewerStyle style) {
        return new CreateInterviewSessionRequest(101L, 35L, "vi", 30, style, null);
    }

    private static class Fixture {
        private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        private final InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        private final CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
        private final UserAccountRepository users = mock(UserAccountRepository.class);
        private final InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        private final InterviewSnapshotFactory snapshotFactory = mock(InterviewSnapshotFactory.class);
        private final InterviewPreparationService preparationService = mock(InterviewPreparationService.class);
        private final InterviewSessionTransitionRecorder transitionRecorder = mock(InterviewSessionTransitionRecorder.class);
        private final AtomicReference<InterviewSession> saved = new AtomicReference<>();
        private final UserAccount user = UserAccount.builder().id(USER_ID).build();
        private final InterviewTemplate template = template();
        private final CandidateProfile profile = profile();
        private final InterviewSessionServiceImpl service;

        private Fixture() {
            service = new InterviewSessionServiceImpl(
                    sessions, templates, profiles, users, focusAreas, snapshotFactory,
                    preparationService, transitionRecorder, new InterviewSessionMapper(),
                    new InterviewSessionProperties(
                            List.of("vi", "en"),
                            List.of(15, 30, 45, 60),
                            List.of(InterviewSessionMode.TURN_BASED,
                                    InterviewSessionMode.VOICE_REALTIME)),
                    new RealtimeProperties(true, "gemini-live"),
                    Clock.fixed(NOW, ZoneOffset.UTC), transactionManager());
        }

        private void stubNewSession() {
            when(sessions.findByUserIdAndIdempotencyKey(USER_ID, "request-1"))
                    .thenReturn(Optional.empty());
            when(templates.findAccessibleForSession(101L, USER_ID))
                    .thenReturn(Optional.of(template));
            when(profiles.findAvailableByIdAndUserId(35L, USER_ID))
                    .thenReturn(Optional.of(profile));
            when(users.getReferenceById(USER_ID)).thenReturn(user);
            when(snapshotFactory.create(template, profile)).thenReturn(
                    new InterviewSnapshotFactory.SnapshotBundle(
                            "{\"template\":1}", "{\"profile\":2}"));
            when(sessions.save(any())).thenAnswer(invocation -> {
                InterviewSession session = invocation.getArgument(0);
                session.setId(501L);
                saved.set(session);
                return session;
            });
            when(sessions.findByIdAndUserId(501L, USER_ID))
                    .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        }

        private InterviewSession session(Long id, InterviewSessionStatus status) {
            return InterviewSession.builder()
                    .id(id)
                    .user(user)
                    .template(template)
                    .profile(profile)
                    .idempotencyKey("request-1")
                    .templateTitleSnapshot(template.getTitle())
                    .profileNameSnapshot(profile.getName())
                    .status(status)
                    .mode(InterviewSessionMode.TURN_BASED)
                    .languageCode("vi")
                    .durationMinutes(30)
                    .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                    .templateSnapshotJson("{}")
                    .profileSnapshotJson("{}")
                    .createdAt(NOW)
                    .updatedAt(NOW)
                    .build();
        }

        private static InterviewTemplate template() {
            InterviewTemplate value = new InterviewTemplate();
            value.setId(101L);
            value.setTitle("Backend Java");
            value.setConfirmedAt(NOW.minusSeconds(60));
            return value;
        }

        private static CandidateProfile profile() {
            return CandidateProfile.builder()
                    .id(35L)
                    .name("Minh profile")
                    .confirmedAt(NOW.minusSeconds(60))
                    .build();
        }
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
}
