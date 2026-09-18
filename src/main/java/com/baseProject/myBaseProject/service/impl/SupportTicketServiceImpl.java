package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.support.CreateSupportTicketRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketPageResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.entity.SupportTicket;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import com.baseProject.myBaseProject.repository.SupportTicketRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportTicketServiceImpl implements SupportTicketService {
    private final SupportTicketRepository tickets;
    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final UserAccountRepository users;
    private final ObjectMapper objectMapper;
    private final Clock clock;

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
        ticket.setSubject(request.subject().strip());
        ticket.setDescription(request.description().strip());
        ticket.setContextJson(contextJson(session, turn));
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return map(tickets.save(ticket));
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
                ticket.getType(), ticket.getStatus(), ticket.getSubject(), ticket.getDescription(),
                ticket.getSession() == null ? null : ticket.getSession().getId(),
                ticket.getTurn() == null ? null : ticket.getTurn().getId(),
                ticket.getContextJson(), ticket.getCreatedAt(), ticket.getUpdatedAt());
    }
}
