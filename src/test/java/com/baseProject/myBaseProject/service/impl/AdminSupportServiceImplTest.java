package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketStatusRequest;
import com.baseProject.myBaseProject.entity.SupportTicket;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.repository.InterviewFeedbackRepository;
import com.baseProject.myBaseProject.repository.SupportTicketEventRepository;
import com.baseProject.myBaseProject.repository.SupportTicketMessageRepository;
import com.baseProject.myBaseProject.repository.SupportTicketRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSupportServiceImplTest {
    @Test
    void resolvingTicketRequiresSummaryAndNotifiesOwner() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        SupportTicketRepository tickets = mock(SupportTicketRepository.class);
        SupportTicketMessageRepository messages = mock(SupportTicketMessageRepository.class);
        SupportTicketEventRepository events = mock(SupportTicketEventRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        AdminAuditService audit = mock(AdminAuditService.class);
        UserAccount owner = UserAccount.builder()
                .id(7L).fullName("Minh").email("minh@example.com").build();
        SupportTicket ticket = new SupportTicket();
        ticket.setId(10L);
        ticket.setReferenceCode("SUP-10");
        ticket.setUser(owner);
        ticket.setType(SupportTicketType.INTERVIEW);
        ticket.setStatus(SupportTicketStatus.IN_REVIEW);
        ticket.setPriority(SupportTicketPriority.HIGH);
        ticket.setSubject("Scoring issue");
        ticket.setDescription("Cannot view report");
        ticket.setCreatedAt(now.minusSeconds(600));
        ticket.setUpdatedAt(now.minusSeconds(60));
        when(tickets.findByIdForUpdate(10L)).thenReturn(Optional.of(ticket));
        when(users.getReferenceById(3L)).thenReturn(
                UserAccount.builder().id(3L).fullName("Admin").email("admin@example.com").build());

        AdminSupportServiceImpl service = new AdminSupportServiceImpl(
                tickets, messages, events, mock(InterviewFeedbackRepository.class), users,
                notifications, audit, Clock.fixed(now, ZoneOffset.UTC));

        var response = service.updateStatus(3L, 10L,
                new UpdateSupportTicketStatusRequest(
                        SupportTicketStatus.RESOLVED, "Scoring was retried"));

        assertThat(response.status()).isEqualTo(SupportTicketStatus.RESOLVED);
        assertThat(response.resolvedAt()).isEqualTo(now);
        assertThat(response.resolutionSummary()).isEqualTo("Scoring was retried");
        verify(notifications).create(eq(7L), eq(UserNotificationType.SUPPORT_TICKET_UPDATED),
                eq("Support request SUP-10 updated"), eq("Status changed to RESOLVED"),
                eq("SUPPORT_TICKET"), eq(10L));
    }
}
