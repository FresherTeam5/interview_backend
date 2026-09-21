package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminAuditLogResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.entity.AdminAuditLog;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.AdminAuditLogRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditServiceImpl implements AdminAuditService {
    private static final String REQUEST_ID_ATTRIBUTE =
            AdminAuditServiceImpl.class.getName() + ".requestId";

    private final AdminAuditLogRepository logs;
    private final UserAccountRepository users;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void record(
            Long actorId,
            AdminAuditAction action,
            String resourceType,
            Object resourceId,
            Object before,
            Object after) {
        RequestMetadata metadata = requestMetadata();
        UserAccount actor = users.getReferenceById(actorId);
        logs.save(new AdminAuditLog(
                actor,
                action,
                normalizeRequired(resourceType, 60),
                resourceId == null ? null : truncate(String.valueOf(resourceId), 100),
                json(before),
                json(after),
                metadata.requestId(),
                metadata.ipAddress(),
                clock.instant()));
    }

    @Override
    public AdminPageResponse<AdminAuditLogResponse> list(
            Long actorId,
            AdminAuditAction action,
            String resourceType,
            String resourceId,
            Instant from,
            Instant to,
            int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "from must be before or equal to to");
        }
        Page<AdminAuditLog> result = logs.search(
                actorId,
                action,
                normalize(resourceType, 60),
                normalize(resourceId, 100),
                from,
                to,
                pageRequest(page, size));
        return new AdminPageResponse<>(result.getContent().stream().map(this::map).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private AdminAuditLogResponse map(AdminAuditLog log) {
        UserAccount actor = log.getActor();
        AdminUserReferenceResponse actorResponse = actor == null ? null
                : new AdminUserReferenceResponse(actor.getId(), actor.getFullName(), actor.getEmail());
        return new AdminAuditLogResponse(log.getId(), actorResponse, log.getAction(),
                log.getResourceType(), log.getResourceId(), log.getBeforeJson(),
                log.getAfterJson(), log.getRequestId(), log.getIpAddress(), log.getCreatedAt());
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR,
                    "Cannot serialize administrator audit data", exception);
        }
    }

    private RequestMetadata requestMetadata() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new RequestMetadata(UUID.randomUUID().toString(), null);
        }
        HttpServletRequest request = attributes.getRequest();
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            requestId = normalize(request.getHeader("X-Request-ID"), 100);
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
            }
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        }
        return new RequestMetadata(requestId, truncate(request.getRemoteAddr(), 64));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private String normalizeRequired(String value, int maxLength) {
        String result = normalize(value, maxLength);
        if (result == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return result;
    }

    private String normalize(String value, int maxLength) {
        return value == null || value.isBlank() ? null : truncate(value.strip(), maxLength);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value : value.substring(0, maxLength);
    }

    private record RequestMetadata(String requestId, String ipAddress) {
    }
}
