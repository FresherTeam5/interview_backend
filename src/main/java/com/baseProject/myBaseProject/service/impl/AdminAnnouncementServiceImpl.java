package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminAnnouncementRequest;
import com.baseProject.myBaseProject.dto.admin.AdminAnnouncementResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.dto.admin.AnnouncementAudiencePreviewResponse;
import com.baseProject.myBaseProject.dto.admin.ScheduleAnnouncementRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateAdminAnnouncementRequest;
import com.baseProject.myBaseProject.entity.AdminAnnouncement;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.AdminAnnouncementRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAnnouncementService;
import com.baseProject.myBaseProject.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnnouncementServiceImpl implements AdminAnnouncementService {
    private final AdminAnnouncementRepository announcements;
    private final UserAccountRepository users;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public AdminPageResponse<AdminAnnouncementResponse> list(
            AnnouncementStatus status, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        Page<AdminAnnouncement> result = status == null
                ? announcements.findAll(pageable) : announcements.findByStatus(status, pageable);
        return new AdminPageResponse<>(result.getContent().stream().map(this::map).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public AdminAnnouncementResponse get(Long id) {
        return map(require(id));
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse create(Long adminId, AdminAnnouncementRequest request) {
        validateChannels(request.inAppEnabled(), request.emailEnabled());
        Instant now = clock.instant();
        AdminAnnouncement value = new AdminAnnouncement();
        value.setCreatedBy(users.getReferenceById(adminId));
        apply(value, request.title(), request.message(), request.audience(),
                request.inAppEnabled(), request.emailEnabled());
        value.setStatus(AnnouncementStatus.DRAFT);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value = announcements.saveAndFlush(value);
        audit.record(adminId, AdminAuditAction.ANNOUNCEMENT_CREATED, "ANNOUNCEMENT",
                value.getId(), null, Map.of("status", value.getStatus(),
                        "audience", value.getAudience()));
        return map(value);
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse update(
            Long adminId, Long id, UpdateAdminAnnouncementRequest request) {
        validateChannels(request.inAppEnabled(), request.emailEnabled());
        AdminAnnouncement value = locked(id);
        requireDraft(value);
        requireVersion(value, request.expectedVersion());
        apply(value, request.title(), request.message(), request.audience(),
                request.inAppEnabled(), request.emailEnabled());
        value.setUpdatedAt(clock.instant());
        announcements.flush();
        audit.record(adminId, AdminAuditAction.ANNOUNCEMENT_UPDATED, "ANNOUNCEMENT",
                id, null, Map.of("audience", value.getAudience(),
                        "inAppEnabled", value.isInAppEnabled(),
                        "emailEnabled", value.isEmailEnabled()));
        return map(value);
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse schedule(
            Long adminId, Long id, ScheduleAnnouncementRequest request) {
        AdminAnnouncement value = locked(id);
        requireDraft(value);
        requireVersion(value, request.expectedVersion());
        Instant now = clock.instant();
        if (request.scheduledAt().isBefore(now)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "scheduledAt must not be in the past");
        }
        value.setStatus(AnnouncementStatus.SCHEDULED);
        value.setScheduledAt(request.scheduledAt());
        value.setUpdatedAt(now);
        announcements.flush();
        audit.record(adminId, AdminAuditAction.ANNOUNCEMENT_SCHEDULED, "ANNOUNCEMENT",
                id, null, Map.of("scheduledAt", request.scheduledAt(),
                        "eligibleRecipients", audienceCount(value.getAudience(), now)));
        return map(value);
    }

    @Override
    @Transactional
    public AdminAnnouncementResponse cancel(Long adminId, Long id, long expectedVersion) {
        AdminAnnouncement value = locked(id);
        if (value.getStatus() == AnnouncementStatus.CANCELLED) {
            return map(value);
        }
        if (value.getStatus() != AnnouncementStatus.DRAFT
                && value.getStatus() != AnnouncementStatus.SCHEDULED) {
            throw new DomainException(ErrorCode.ANNOUNCEMENT_STATE_INVALID);
        }
        requireVersion(value, expectedVersion);
        value.setStatus(AnnouncementStatus.CANCELLED);
        value.setUpdatedAt(clock.instant());
        announcements.flush();
        audit.record(adminId, AdminAuditAction.ANNOUNCEMENT_CANCELLED, "ANNOUNCEMENT",
                id, null, Map.of("status", AnnouncementStatus.CANCELLED));
        return map(value);
    }

    @Override
    public AnnouncementAudiencePreviewResponse preview(AnnouncementAudience audience) {
        return new AnnouncementAudiencePreviewResponse(
                audience, audienceCount(audience, clock.instant()));
    }

    private long audienceCount(AnnouncementAudience audience, Instant now) {
        String condition = switch (audience) {
            case ALL_USERS -> "";
            case VERIFIED_USERS -> " AND email_verified_at IS NOT NULL";
            case ACTIVE_USERS -> " AND last_login_at >= ?";
        };
        String sql = """
                SELECT COUNT(*)
                FROM user_accounts
                WHERE role = 'USER'
                  AND enabled = TRUE
                  AND deletion_requested_at IS NULL
                  AND (suspended_at IS NULL
                       OR (suspended_until IS NOT NULL AND suspended_until <= ?))
                """ + condition;
        Long count = audience == AnnouncementAudience.ACTIVE_USERS
                ? jdbc.queryForObject(sql, Long.class, now,
                        now.minus(30, ChronoUnit.DAYS))
                : jdbc.queryForObject(sql, Long.class, now);
        return count == null ? 0 : count;
    }

    private void apply(
            AdminAnnouncement value,
            String title,
            String message,
            AnnouncementAudience audience,
            boolean inApp,
            boolean email) {
        value.setTitle(title.strip());
        value.setMessage(message.strip());
        value.setAudience(audience);
        value.setInAppEnabled(inApp);
        value.setEmailEnabled(email);
    }

    private AdminAnnouncementResponse map(AdminAnnouncement value) {
        UserAccount creator = value.getCreatedBy();
        AdminUserReferenceResponse creatorResponse = creator == null ? null
                : new AdminUserReferenceResponse(
                        creator.getId(), creator.getFullName(), creator.getEmail());
        return new AdminAnnouncementResponse(value.getId(), creatorResponse, value.getTitle(),
                value.getMessage(), value.getAudience(), value.isInAppEnabled(),
                value.isEmailEnabled(), value.getStatus(), value.getScheduledAt(),
                value.getStartedAt(), value.getCompletedAt(), value.getTotalRecipients(),
                value.getDeliveredCount(), value.getFailedCount(), value.getLastError(),
                value.getVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private AdminAnnouncement require(Long id) {
        return announcements.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }

    private AdminAnnouncement locked(Long id) {
        return announcements.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }

    private void requireDraft(AdminAnnouncement value) {
        if (value.getStatus() != AnnouncementStatus.DRAFT) {
            throw new DomainException(ErrorCode.ANNOUNCEMENT_STATE_INVALID);
        }
    }

    private void requireVersion(AdminAnnouncement value, long expected) {
        if (expected < 0 || value.getVersion() != expected) {
            throw new DomainException(ErrorCode.ANNOUNCEMENT_VERSION_CONFLICT);
        }
    }

    private void validateChannels(boolean inApp, boolean email) {
        if (!inApp && !email) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "At least one announcement channel is required");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
