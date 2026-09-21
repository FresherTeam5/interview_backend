package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminSessionActionRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.support.InterviewSessionCloser;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionTransitionRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.InterviewReportService;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminInterviewSessionOperationsTest {
    @Test
    void terminateMarksSessionCancelledWithSystemReasonAndTransition() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewSessionTransitionRepository transitions =
                mock(InterviewSessionTransitionRepository.class);
        InterviewSessionTransitionRecorder recorder = mock(InterviewSessionTransitionRecorder.class);
        InterviewSession session = InterviewSession.builder()
                .id(50L)
                .user(UserAccount.builder().id(7L).fullName("Minh")
                        .email("minh@example.com").build())
                .status(InterviewSessionStatus.PREPARATION_FAILED)
                .mode(InterviewSessionMode.TURN_BASED)
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .templateTitleSnapshot("Backend")
                .profileNameSnapshot("Minh")
                .createdAt(now.minusSeconds(600))
                .updatedAt(now.minusSeconds(30))
                .build();
        when(sessions.findByIdForUpdate(50L)).thenReturn(Optional.of(session));
        when(sessions.findByIdForAdmin(50L)).thenReturn(Optional.of(session));
        when(transitions.findBySessionIdOrderByOccurredAtAsc(50L)).thenReturn(List.of());

        AdminInterviewSessionServiceImpl service = new AdminInterviewSessionServiceImpl(
                sessions, transitions, mock(InterviewSessionService.class),
                mock(InterviewReportService.class), mock(InterviewSessionCloser.class),
                recorder, mock(AdminAuditService.class), Clock.fixed(now, ZoneOffset.UTC));

        var result = service.terminate(3L, 50L,
                new AdminSessionActionRequest("Session cannot be recovered"));

        assertThat(result.status()).isEqualTo(InterviewSessionStatus.CANCELLED);
        assertThat(session.getEndReason()).isEqualTo(InterviewEndReason.SYSTEM_TERMINATED);
        verify(recorder).record(any(),
                org.mockito.ArgumentMatchers.eq(InterviewSessionStatus.PREPARATION_FAILED),
                org.mockito.ArgumentMatchers.eq(InterviewSessionStatus.CANCELLED),
                org.mockito.ArgumentMatchers.eq("Session cannot be recovered"),
                org.mockito.ArgumentMatchers.eq(
                        com.baseProject.myBaseProject.enums.InterviewTransitionActor.ADMIN),
                org.mockito.ArgumentMatchers.eq(now));
    }
}
