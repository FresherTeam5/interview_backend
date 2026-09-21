package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminAddSupportMessageRequest;
import com.baseProject.myBaseProject.dto.admin.AdminInterviewFeedbackResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketEventResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.dto.admin.AssignSupportTicketRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketPriorityRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketStatusRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;
import com.baseProject.myBaseProject.entity.InterviewFeedback;
import com.baseProject.myBaseProject.entity.SupportTicket;
import com.baseProject.myBaseProject.entity.SupportTicketEvent;
import com.baseProject.myBaseProject.entity.SupportTicketMessage;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.SupportMessageVisibility;
import com.baseProject.myBaseProject.enums.SupportTicketEventType;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewFeedbackRepository;
import com.baseProject.myBaseProject.repository.SupportTicketEventRepository;
import com.baseProject.myBaseProject.repository.SupportTicketMessageRepository;
import com.baseProject.myBaseProject.repository.SupportTicketRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.AdminSupportService;
import com.baseProject.myBaseProject.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupportServiceImpl implements AdminSupportService {
    private static final Map<SupportTicketStatus, Set<SupportTicketStatus>> TRANSITIONS = Map.of(
            SupportTicketStatus.OPEN,
            EnumSet.of(SupportTicketStatus.IN_REVIEW, SupportTicketStatus.CLOSED),
            SupportTicketStatus.IN_REVIEW,
            EnumSet.of(SupportTicketStatus.RESOLVED, SupportTicketStatus.CLOSED),
            SupportTicketStatus.RESOLVED,
            EnumSet.of(SupportTicketStatus.IN_REVIEW, SupportTicketStatus.CLOSED),
            SupportTicketStatus.CLOSED,
            EnumSet.of(SupportTicketStatus.IN_REVIEW));

    private final SupportTicketRepository tickets;
    private final SupportTicketMessageRepository messages;
    private final SupportTicketEventRepository events;
    private final InterviewFeedbackRepository feedback;
    private final UserAccountRepository users;
    private final NotificationService notifications;
    private final AdminAuditService audit;
    private final Clock clock;

    @Override
    public AdminPageResponse<AdminSupportTicketSummaryResponse> list(
            String keyword, SupportTicketStatus status, SupportTicketType type,
            SupportTicketPriority priority, Long assignedAdminId,
            Instant from, Instant to, int page, int size) {
        validateRange(from, to);
        Page<SupportTicket> result = tickets.searchForAdmin(contains(keyword), status, type,
                priority, assignedAdminId, from, to, pageRequest(page, size));
        return new AdminPageResponse<>(result.getContent().stream().map(this::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public AdminSupportTicketDetailResponse get(Long ticketId) {
        SupportTicket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_NOT_FOUND));
        return detail(ticket);
    }

    @Override
    @Transactional
    public AdminSupportTicketDetailResponse assign(
            Long actorId, Long ticketId, AssignSupportTicketRequest request) {
        SupportTicket ticket = locked(ticketId);
        UserAccount previous = ticket.getAssignedAdmin();
        UserAccount assigned = null;
        if (request.adminId() != null) {
            assigned = users.findById(request.adminId())
                    .filter(user -> user.getRole() == UserRole.ADMIN && user.isEnabled())
                    .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_ASSIGNEE_INVALID));
        }
        if (sameUser(previous, assigned)) {
            return detail(ticket);
        }
        Instant now = clock.instant();
        ticket.setAssignedAdmin(assigned);
        ticket.setUpdatedAt(now);
        event(ticket, users.getReferenceById(actorId), SupportTicketEventType.ASSIGNED,
                id(previous), id(assigned), null, now);
        audit.record(actorId, AdminAuditAction.SUPPORT_ASSIGNED, "SUPPORT_TICKET",
                ticketId, Map.of("assignedAdminId", nullableId(previous)),
                Map.of("assignedAdminId", nullableId(assigned)));
        return detail(ticket);
    }

    @Override
    @Transactional
    public AdminSupportTicketDetailResponse updatePriority(
            Long actorId, Long ticketId, UpdateSupportTicketPriorityRequest request) {
        SupportTicket ticket = locked(ticketId);
        SupportTicketPriority previous = ticket.getPriority();
        if (previous == request.priority()) {
            return detail(ticket);
        }
        Instant now = clock.instant();
        ticket.setPriority(request.priority());
        ticket.setUpdatedAt(now);
        event(ticket, users.getReferenceById(actorId), SupportTicketEventType.PRIORITY_CHANGED,
                previous.name(), request.priority().name(), null, now);
        audit.record(actorId, AdminAuditAction.SUPPORT_PRIORITY_CHANGED, "SUPPORT_TICKET",
                ticketId, Map.of("priority", previous), Map.of("priority", request.priority()));
        return detail(ticket);
    }

    @Override
    @Transactional
    public AdminSupportTicketDetailResponse updateStatus(
            Long actorId, Long ticketId, UpdateSupportTicketStatusRequest request) {
        SupportTicket ticket = locked(ticketId);
        SupportTicketStatus previous = ticket.getStatus();
        String resolution = normalize(request.resolutionSummary());
        if (request.status() == SupportTicketStatus.RESOLVED && resolution == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "resolutionSummary is required when resolving a ticket");
        }
        if (previous == request.status()) {
            if (previous == SupportTicketStatus.RESOLVED
                    && !Objects.equals(ticket.getResolutionSummary(), resolution)) {
                String previousResolution = ticket.getResolutionSummary();
                Instant now = clock.instant();
                ticket.setResolutionSummary(resolution);
                ticket.setUpdatedAt(now);
                event(ticket, users.getReferenceById(actorId),
                        SupportTicketEventType.STATUS_CHANGED,
                        previous.name(), previous.name(), "Resolution summary updated", now);
                audit.record(actorId, AdminAuditAction.SUPPORT_STATUS_CHANGED,
                        "SUPPORT_TICKET", ticketId,
                        statusAudit(previous, previousResolution),
                        statusAudit(previous, resolution));
                notifyUser(ticket, "Support request " + ticket.getReferenceCode() + " updated",
                        "Resolution summary was updated");
            }
            return detail(ticket);
        }
        if (!TRANSITIONS.getOrDefault(previous, Set.of()).contains(request.status())) {
            throw new DomainException(ErrorCode.SUPPORT_TICKET_TRANSITION_INVALID);
        }

        Instant now = clock.instant();
        ticket.setStatus(request.status());
        ticket.setUpdatedAt(now);
        if (request.status() == SupportTicketStatus.RESOLVED) {
            ticket.setResolutionSummary(resolution);
            ticket.setResolvedAt(now);
            ticket.setClosedAt(null);
        } else if (request.status() == SupportTicketStatus.CLOSED) {
            ticket.setClosedAt(now);
        } else if (request.status() == SupportTicketStatus.IN_REVIEW) {
            ticket.setResolutionSummary(null);
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
        }
        UserAccount actor = users.getReferenceById(actorId);
        event(ticket, actor, SupportTicketEventType.STATUS_CHANGED,
                previous.name(), request.status().name(), resolution, now);
        audit.record(actorId, AdminAuditAction.SUPPORT_STATUS_CHANGED, "SUPPORT_TICKET",
                ticketId, Map.of("status", previous), Map.of("status", request.status()));
        notifyUser(ticket, "Support request " + ticket.getReferenceCode() + " updated",
                "Status changed to " + request.status().name());
        return detail(ticket);
    }

    @Override
    @Transactional
    public SupportTicketMessageResponse addMessage(
            Long actorId, Long ticketId, AdminAddSupportMessageRequest request) {
        SupportTicket ticket = locked(ticketId);
        if (ticket.getStatus() == SupportTicketStatus.CLOSED && !request.internal()) {
            throw new DomainException(ErrorCode.SUPPORT_TICKET_CLOSED);
        }
        Instant now = clock.instant();
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setSender(users.getReferenceById(actorId));
        message.setVisibility(request.internal()
                ? SupportMessageVisibility.INTERNAL : SupportMessageVisibility.PUBLIC);
        message.setMessage(request.message().strip());
        message.setCreatedAt(now);
        message = messages.save(message);
        ticket.setUpdatedAt(now);
        event(ticket, message.getSender(), SupportTicketEventType.MESSAGE_ADDED,
                null, String.valueOf(message.getId()),
                request.internal() ? "Internal note" : "Public reply", now);
        audit.record(actorId, AdminAuditAction.SUPPORT_MESSAGE_ADDED, "SUPPORT_TICKET",
                ticketId, null, Map.of("messageId", message.getId(),
                        "visibility", message.getVisibility()));
        if (!request.internal()) {
            notifyUser(ticket, "New reply for " + ticket.getReferenceCode(),
                    truncate(message.getMessage(), 1000));
        }
        return message(message);
    }

    @Override
    public AdminPageResponse<AdminInterviewFeedbackResponse> listFeedback(
            String keyword, Integer rating, Instant from, Instant to, int page, int size) {
        validateRange(from, to);
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "rating must be between 1 and 5");
        }
        Page<InterviewFeedback> result = feedback.searchForAdmin(
                contains(keyword), rating, from, to, pageRequest(page, size));
        return new AdminPageResponse<>(result.getContent().stream().map(this::feedback).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private AdminSupportTicketSummaryResponse summary(SupportTicket ticket) {
        return new AdminSupportTicketSummaryResponse(ticket.getId(), ticket.getReferenceCode(),
                user(ticket.getUser()), ticket.getType(), ticket.getStatus(), ticket.getPriority(),
                ticket.getSubject(), user(ticket.getAssignedAdmin()),
                ticket.getSession() == null ? null : ticket.getSession().getId(),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    private AdminSupportTicketDetailResponse detail(SupportTicket ticket) {
        return new AdminSupportTicketDetailResponse(ticket.getId(), ticket.getReferenceCode(),
                user(ticket.getUser()), ticket.getType(), ticket.getStatus(), ticket.getPriority(),
                ticket.getSubject(), ticket.getDescription(), user(ticket.getAssignedAdmin()),
                ticket.getSession() == null ? null : ticket.getSession().getId(),
                ticket.getTurn() == null ? null : ticket.getTurn().getId(),
                ticket.getContextJson(), ticket.getResolutionSummary(), ticket.getResolvedAt(),
                ticket.getClosedAt(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                        .map(this::message).toList(),
                events.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                        .map(this::event).toList());
    }

    private AdminInterviewFeedbackResponse feedback(InterviewFeedback value) {
        return new AdminInterviewFeedbackResponse(value.getId(), value.getSession().getId(),
                user(value.getUser()), value.getQuestionRating(), value.getVoiceRating(),
                value.getReportRating(), value.getComment(), value.getCreatedAt(),
                value.getUpdatedAt());
    }

    private SupportTicketMessageResponse message(SupportTicketMessage value) {
        UserAccount sender = value.getSender();
        return new SupportTicketMessageResponse(value.getId(),
                sender == null ? null : sender.getId(),
                sender == null ? "Deleted account" : sender.getFullName(),
                sender == null ? null : sender.getRole(), value.getVisibility(),
                value.getMessage(), value.getCreatedAt());
    }

    private AdminSupportTicketEventResponse event(SupportTicketEvent value) {
        return new AdminSupportTicketEventResponse(value.getId(), user(value.getActor()),
                value.getEventType(), value.getFromValue(), value.getToValue(), value.getNote(),
                value.getCreatedAt());
    }

    private void event(
            SupportTicket ticket, UserAccount actor, SupportTicketEventType type,
            String from, String to, String note, Instant now) {
        SupportTicketEvent value = new SupportTicketEvent();
        value.setTicket(ticket);
        value.setActor(actor);
        value.setEventType(type);
        value.setFromValue(from);
        value.setToValue(to);
        value.setNote(note);
        value.setCreatedAt(now);
        events.save(value);
    }

    private SupportTicket locked(Long ticketId) {
        return tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new DomainException(ErrorCode.SUPPORT_TICKET_NOT_FOUND));
    }

    private AdminUserReferenceResponse user(UserAccount value) {
        return value == null ? null
                : new AdminUserReferenceResponse(value.getId(), value.getFullName(), value.getEmail());
    }

    private boolean sameUser(UserAccount first, UserAccount second) {
        return first == null ? second == null
                : second != null && first.getId().equals(second.getId());
    }

    private String id(UserAccount user) {
        return user == null ? null : String.valueOf(user.getId());
    }

    private Object nullableId(UserAccount user) {
        return user == null ? "UNASSIGNED" : user.getId();
    }

    private void notifyUser(SupportTicket ticket, String title, String message) {
        notifications.create(ticket.getUser().getId(), UserNotificationType.SUPPORT_TICKET_UPDATED,
                title, message, "SUPPORT_TICKET", ticket.getId());
    }

    private Map<String, Object> statusAudit(
            SupportTicketStatus status, String resolutionSummary) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put("resolutionSummary", resolutionSummary);
        return value;
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "from must be before or equal to to");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
    }

    private String contains(String value) {
        return value == null || value.isBlank()
                ? null : "%" + value.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
