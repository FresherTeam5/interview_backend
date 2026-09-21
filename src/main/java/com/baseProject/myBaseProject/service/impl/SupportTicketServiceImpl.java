package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.support.CreateSupportTicketRequest;
import com.baseProject.myBaseProject.dto.support.CreateSupportMessageRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketPageResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.entity.SupportTicket;
import com.baseProject.myBaseProject.entity.SupportTicketEvent;
import com.baseProject.myBaseProject.entity.SupportTicketMessage;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.SupportMessageVisibility;
import com.baseProject.myBaseProject.enums.SupportTicketEventType;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import com.baseProject.myBaseProject.repository.SupportTicketRepository;
import com.baseProject.myBaseProject.repository.SupportTicketEventRepository;
import com.baseProject.myBaseProject.repository.SupportTicketMessageRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.SupportTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupportTicketServiceImpl implements SupportTicketService {
    private final SupportTicketRepository tickets;
    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final UserAccountRepository users;
    private final SupportTicketMessageRepository messages;
    private final SupportTicketEventRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SupportTicketServiceImpl(
            SupportTicketRepository tickets,
            InterviewSessionRepository sessions,
            InterviewTurnRepository turns,
            UserAccountRepository users,
            SupportTicketMessageRepository messages,
            SupportTicketEventRepository events,
            ObjectMapper objectMapper,
            Clock clock) {
        this.tickets = tickets;
        this.sessions = sessions;
        this.turns = turns;
        this.users = users;
        this.messages = messages;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // Giữ constructor cũ để các unit test và consumer nội bộ không bị vỡ.
    public SupportTicketServiceImpl(
            SupportTicketRepository tickets,
            InterviewSessionRepository sessions,
            InterviewTurnRepository turns,
            UserAccountRepository users,
            ObjectMapper objectMapper,
            Clock clock) {
        this(tickets, sessions, turns, users, null, null, objectMapper, clock);
    }

    @Override
    @Transactional
    public SupportTicketResponse create(Long userId, CreateSupportTicketRequest request) {
        InterviewSession session = null;
        InterviewTurn turn = null;
        if (request.sessionId() != null) {
            session = sessions.findByIdAndUserId(request.sessionId(), userId)
                    .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_CONTEXT_INVALID));
        }
        if (request.turnId() != null) {
            turn = turns.findById(request.turnId())
                    .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_CONTEXT_INVALID));
            InterviewSession turnSession = turn.getSession();
            if (turnSession.getUser() == null || !userId.equals(turnSession.getUser().getId())
                    || (session != null && !session.getId().equals(turnSession.getId()))) {
                throw new DomainException(ErrorCode.SUPPORT_TICKET_CONTEXT_INVALID);
            }
            session = turnSession;
        }

        Instant now = clock.instant();
        SupportTicket ticket = new SupportTicket();
        ticket.setReferenceCode("SUP-" + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase(java.util.Locale.ROOT));
        ticket.setUser(users.getReferenceById(userId));
        ticket.setSession(session);
        ticket.setTurn(turn);
        ticket.setType(request.type());
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setPriority(SupportTicketPriority.NORMAL);
        ticket.setSubject(request.subject().strip());
        ticket.setDescription(request.description().strip());
        ticket.setContextJson(contextJson(session, turn));
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        ticket = tickets.save(ticket);
        addEvent(ticket, ticket.getUser(), SupportTicketEventType.CREATED,
                null, SupportTicketStatus.OPEN.name(), null, now);
        return map(ticket);
    }

    @Override
    public SupportTicketPageResponse list(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        Page<SupportTicket> result = tickets.findByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new SupportTicketPageResponse(result.map(this::map).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public SupportTicketResponse get(Long userId, Long ticketId) {
        return tickets.findByIdAndUserId(ticketId, userId)
                .map(this::map)
                .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_NOT_FOUND));
    }

    @Override
    public List<SupportTicketMessageResponse> messages(Long userId, Long ticketId) {
        requireOwnedTicket(userId, ticketId);
        if (messages == null) {
            return List.of();
        }
        return messages.findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(
                        ticketId, SupportMessageVisibility.PUBLIC)
                .stream().map(this::mapMessage).toList();
    }

    @Override
    @Transactional
    public SupportTicketMessageResponse addMessage(
            Long userId, Long ticketId, CreateSupportMessageRequest request) {
        SupportTicket ticket = tickets.findByIdForUpdate(ticketId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_NOT_FOUND));
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new DomainException(ErrorCode.SUPPORT_TICKET_CLOSED);
        }
        Instant now = clock.instant();
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setSender(ticket.getUser());
        message.setVisibility(SupportMessageVisibility.PUBLIC);
        message.setMessage(request.message().strip());
        message.setCreatedAt(now);
        message = messages.save(message);
        ticket.setUpdatedAt(now);
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            ticket.setStatus(SupportTicketStatus.IN_REVIEW);
            ticket.setResolutionSummary(null);
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
            addEvent(ticket, ticket.getUser(), SupportTicketEventType.STATUS_CHANGED,
                    SupportTicketStatus.RESOLVED.name(),
                    SupportTicketStatus.IN_REVIEW.name(),
                    "User replied after resolution", now);
        }
        addEvent(ticket, ticket.getUser(), SupportTicketEventType.MESSAGE_ADDED,
                null, String.valueOf(message.getId()), null, now);
        return mapMessage(message);
    }

    private String contextJson(InterviewSession session, InterviewTurn turn) {
        if (session == null) {
            return null;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", session.getId());
        context.put("sessionStatus", session.getStatus());
        context.put("sessionMode", session.getMode());
        context.put("languageCode", session.getLanguageCode());
        context.put("createdAt", session.getCreatedAt());
        context.put("preparationErrorCode", session.getPreparationErrorCode());
        context.put("scoringErrorCode", session.getScoringErrorCode());
        if (turn != null) {
            context.put("turnId", turn.getId());
            context.put("turnIndex", turn.getTurnIndex());
            context.put("turnRole", turn.getRole());
            context.put("turnProcessingStatus", turn.getProcessingStatus());
            context.put("turnErrorCode", turn.getProcessingErrorCode());
        }
        return objectMapper.writeValueAsString(context);
    }

    private SupportTicketResponse map(SupportTicket ticket) {
        return new SupportTicketResponse(ticket.getId(), ticket.getReferenceCode(),
                ticket.getType(), ticket.getStatus(), ticket.getPriority(),
                ticket.getSubject(), ticket.getDescription(),
                ticket.getSession() == null ? null : ticket.getSession().getId(),
                ticket.getTurn() == null ? null : ticket.getTurn().getId(),
                ticket.getContextJson(), ticket.getResolutionSummary(), ticket.getResolvedAt(),
                ticket.getClosedAt(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private SupportTicket requireOwnedTicket(Long userId, Long ticketId) {
        return tickets.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_NOT_FOUND));
    }

    private SupportTicketMessageResponse mapMessage(SupportTicketMessage message) {
        UserAccount sender = message.getSender();
        return new SupportTicketMessageResponse(message.getId(),
                sender == null ? null : sender.getId(),
                sender == null ? "Deleted account" : sender.getFullName(),
                sender == null ? null : sender.getRole(),
                message.getVisibility(), message.getMessage(), message.getCreatedAt());
    }

    private void addEvent(
            SupportTicket ticket,
            UserAccount actor,
            SupportTicketEventType type,
            String from,
            String to,
            String note,
            Instant now) {
        if (events == null) {
            return;
        }
        SupportTicketEvent event = new SupportTicketEvent();
        event.setTicket(ticket);
        event.setActor(actor);
        event.setEventType(type);
        event.setFromValue(from);
        event.setToValue(to);
        event.setNote(note);
        event.setCreatedAt(now);
        events.save(event);
    }
}
