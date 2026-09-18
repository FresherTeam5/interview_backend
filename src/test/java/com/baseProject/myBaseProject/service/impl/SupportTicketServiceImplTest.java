package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.support.CreateSupportTicketRequest;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.entity.SupportTicket;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import com.baseProject.myBaseProject.repository.SupportTicketRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupportTicketServiceImplTest {
    @Test
    void capturesOwnedSessionAndTurnDiagnosticsAtCreationTime() {
        Instant now = Instant.parse("2026-09-18T03:00:00Z");
        SupportTicketRepository tickets = mock(SupportTicketRepository.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        UserAccount user = UserAccount.builder().id(7L).build();
        InterviewSession session = InterviewSession.builder()
                .id(51L).user(user).status(InterviewSessionStatus.IN_PROGRESS)
                .mode(InterviewSessionMode.TURN_BASED).languageCode("vi")
                .createdAt(now.minusSeconds(120)).build();
        InterviewTurn turn = InterviewTurn.builder()
                .id(63L).session(session).turnIndex(3).role(InterviewTurnRole.CANDIDATE)
                .contentText("answer").idempotencyKey("answer-2")
                .processingStatus(InterviewTurnProcessingStatus.FAILED)
                .processingErrorCode("AI_TIMEOUT").createdAt(now.minusSeconds(2)).build();
        when(sessions.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(session));
        when(turns.findById(63L)).thenReturn(Optional.of(turn));
        when(users.getReferenceById(7L)).thenReturn(user);
        when(tickets.save(any())).thenAnswer(invocation -> {
            SupportTicket saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        });
        SupportTicketServiceImpl service = new SupportTicketServiceImpl(
                tickets, sessions, turns, users, new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));

        var response = service.create(7L, new CreateSupportTicketRequest(
                SupportTicketType.INTERVIEW, "Cannot retry", "The turn is still failed",
                51L, 63L));

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.referenceCode()).startsWith("SUP-").hasSize(36);
        assertThat(response.status()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(response.contextJson()).contains(
                "\"sessionStatus\":\"IN_PROGRESS\"",
                "\"turnErrorCode\":\"AI_TIMEOUT\"");
    }
}
