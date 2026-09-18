package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.feedback.UpsertInterviewFeedbackRequest;
import com.baseProject.myBaseProject.entity.InterviewFeedback;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewFeedbackRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewFeedbackServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-18T03:00:00Z");

    @Test
    void upsertsFeedbackOnlyForCompletedOwnedSession() {
        InterviewFeedbackRepository feedback = mock(InterviewFeedbackRepository.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        UserAccount user = UserAccount.builder().id(7L).build();
        InterviewSession session = InterviewSession.builder()
                .id(51L).user(user).status(InterviewSessionStatus.COMPLETED).build();
        when(sessions.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(session));
        when(feedback.findBySessionIdAndUserId(51L, 7L)).thenReturn(Optional.empty());
        when(feedback.save(any())).thenAnswer(invocation -> {
            InterviewFeedback saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        InterviewFeedbackServiceImpl service = new InterviewFeedbackServiceImpl(
                feedback, sessions, Clock.fixed(NOW, ZoneOffset.UTC));

        var response = service.upsert(
                7L, 51L, new UpsertInterviewFeedbackRequest(5, 4, 5, " Useful "));

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.sessionId()).isEqualTo(51L);
        assertThat(response.questionRating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Useful");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsFeedbackBeforeReportCompletes() {
        InterviewFeedbackRepository feedback = mock(InterviewFeedbackRepository.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewSession session = InterviewSession.builder()
                .id(51L).status(InterviewSessionStatus.SCORING).build();
        when(sessions.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(session));
        InterviewFeedbackServiceImpl service = new InterviewFeedbackServiceImpl(
                feedback, sessions, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.upsert(
                7L, 51L, new UpsertInterviewFeedbackRequest(5, null, null, null)))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_FEEDBACK_NOT_AVAILABLE));
    }
}
