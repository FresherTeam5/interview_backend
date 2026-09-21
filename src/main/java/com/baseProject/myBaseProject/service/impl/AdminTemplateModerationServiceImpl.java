package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateMetadataRequest;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateReviewRequest;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.dto.template.TemplateVersionRequest;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.TemplateModerationAction;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.mapper.InterviewTemplateMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.TemplateFavoriteRepository;
import com.baseProject.myBaseProject.repository.TemplateViewRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.AdminTemplateModerationService;
import com.baseProject.myBaseProject.service.NotificationService;
import com.baseProject.myBaseProject.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTemplateModerationServiceImpl implements AdminTemplateModerationService {
    private final InterviewTemplateRepository templates;
    private final TemplateViewRepository views;
    private final TemplateFavoriteRepository favorites;
    private final InterviewSessionRepository sessions;
    private final UserAccountRepository users;
    private final InterviewTemplateMapper mapper;
    private final NotificationService notifications;
    private final AdminAuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SystemSettingService systemSettings;

    @Override
    public AdminPageResponse<AdminTemplateSummaryResponse> list(
            String keyword, Long ownerId, TemplateModerationStatus status,
            Boolean published, Boolean featured, String category, int page, int size) {
        Page<InterviewTemplate> result = templates.searchForAdmin(
                contains(keyword), ownerId, status, published, featured, exact(category),
                pageRequest(page, size));
        return new AdminPageResponse<>(result.getContent().stream().map(this::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public AdminTemplateDetailResponse get(Long templateId) {
        return detail(requireTemplate(templateId));
    }

    @Override
    @Transactional
    public AdminTemplateDetailResponse review(
            Long adminId, Long templateId, AdminTemplateReviewRequest request) {
        InterviewTemplate template = locked(templateId);
        requireActive(template);
        if (isSameReviewState(template.getModerationStatus(), request.action())) {
            return detail(template);
        }
        requireVersion(template, request.expectedVersion());
        TemplateModerationStatus previous = template.getModerationStatus();
        String reason = normalize(request.reason());
        validateReview(previous, request.action(), reason, template.isConfirmed());

        Instant now = clock.instant();
        template.setReviewedBy(users.getReferenceById(adminId));
        template.setReviewedAt(now);
        template.setModerationReason(reason);
        UserNotificationType notificationType;
        String notificationMessage;
        switch (request.action()) {
            case APPROVE -> {
                template.setModerationStatus(TemplateModerationStatus.APPROVED);
                notificationType = UserNotificationType.TEMPLATE_APPROVED;
                notificationMessage = "Your interview template was approved";
            }
            case REJECT -> {
                template.setModerationStatus(TemplateModerationStatus.REJECTED);
                template.setConfirmedAt(null);
                template.setPublishedAt(null);
                notificationType = UserNotificationType.TEMPLATE_REJECTED;
                notificationMessage = "Your interview template needs changes: " + reason;
            }
            case HIDE -> {
                template.setModerationStatus(TemplateModerationStatus.HIDDEN);
                template.setPublishedAt(null);
                template.setFeatured(false);
                notificationType = UserNotificationType.TEMPLATE_HIDDEN;
                notificationMessage = "Your interview template was hidden: " + reason;
            }
            default -> throw new DomainException(ErrorCode.TEMPLATE_REVIEW_NOT_ALLOWED);
        }
        template.setUpdatedAt(now);
        templates.flush();
        audit.record(adminId, AdminAuditAction.TEMPLATE_REVIEWED, "INTERVIEW_TEMPLATE",
                templateId, Map.of("moderationStatus", previous),
                reviewAudit(template, request.action()));
        notifications.create(template.getOwner().getId(), notificationType,
                "Template review: " + template.getTitle(), notificationMessage,
                "INTERVIEW_TEMPLATE", templateId);
        return detail(template);
    }

    @Override
    @Transactional
    public AdminTemplateDetailResponse updateMetadata(
            Long adminId, Long templateId, AdminTemplateMetadataRequest request) {
        InterviewTemplate template = locked(templateId);
        requireActive(template);
        requireVersion(template, request.expectedVersion());
        Map<String, Object> before = metadataAudit(template);
        template.setCategory(normalize(request.category()));
        template.setTagsJson(tagsJson(request.tags()));
        template.setFeatured(request.featured());
        template.setDisplayOrder(request.displayOrder());
        template.setUpdatedAt(clock.instant());
        templates.flush();
        audit.record(adminId, AdminAuditAction.TEMPLATE_METADATA_CHANGED,
                "INTERVIEW_TEMPLATE", templateId, before, metadataAudit(template));
        return detail(template);
    }

    @Override
    @Transactional
    public AdminTemplateDetailResponse publish(
            Long adminId, Long templateId, TemplateVersionRequest request) {
        InterviewTemplate template = locked(templateId);
        requireActive(template);
        if (template.isPublished()) {
            return detail(template);
        }
        requireVersion(template, request.expectedVersion());
        if (!template.isConfirmed()) {
            throw new DomainException(ErrorCode.TEMPLATE_CONFIRM_REQUIRED);
        }
        if (systemSettings.booleanValue(SystemSettingServiceImpl.TEMPLATE_REVIEW_REQUIRED, true)
                && template.getModerationStatus() != TemplateModerationStatus.APPROVED) {
            throw new DomainException(ErrorCode.TEMPLATE_APPROVAL_REQUIRED);
        }
        template.setPublishedAt(clock.instant());
        template.setUpdatedAt(clock.instant());
        templates.flush();
        audit.record(adminId, AdminAuditAction.TEMPLATE_PUBLISHED,
                "INTERVIEW_TEMPLATE", templateId, null,
                Map.of("published", true));
        return detail(template);
    }

    @Override
    @Transactional
    public AdminTemplateDetailResponse unpublish(
            Long adminId, Long templateId, TemplateVersionRequest request) {
        InterviewTemplate template = locked(templateId);
        requireActive(template);
        if (!template.isPublished()) {
            return detail(template);
        }
        requireVersion(template, request.expectedVersion());
        template.setPublishedAt(null);
        template.setUpdatedAt(clock.instant());
        templates.flush();
        audit.record(adminId, AdminAuditAction.TEMPLATE_UNPUBLISHED,
                "INTERVIEW_TEMPLATE", templateId, null,
                Map.of("published", false));
        return detail(template);
    }

    private AdminTemplateSummaryResponse summary(InterviewTemplate template) {
        return new AdminTemplateSummaryResponse(template.getId(), user(template.getOwner()),
                template.getTitle(), template.getJobTitle(), template.getTargetSeniority(),
                template.getModerationStatus(), template.isConfirmed(), template.isPublished(),
                template.isFeatured(), template.getCategory(), template.getTagsJson(),
                template.getSubmittedAt(), template.getReviewedAt(),
                template.getModerationReason(), template.getArchivedAt(), template.getVersion(),
                template.getCreatedAt(), template.getUpdatedAt());
    }

    private AdminTemplateDetailResponse detail(InterviewTemplate template) {
        return new AdminTemplateDetailResponse(mapper.toResponse(template), user(template.getOwner()),
                user(template.getReviewedBy()), views.totalViews(template.getId()),
                favorites.countByTemplateId(template.getId()),
                sessions.countByTemplateId(template.getId()));
    }

    private void validateReview(
            TemplateModerationStatus current, TemplateModerationAction action,
            String reason, boolean confirmed) {
        boolean allowed = switch (action) {
            case APPROVE -> confirmed && (current == TemplateModerationStatus.PENDING_REVIEW
                    || current == TemplateModerationStatus.HIDDEN);
            case REJECT -> current == TemplateModerationStatus.PENDING_REVIEW && reason != null;
            case HIDE -> (current == TemplateModerationStatus.PENDING_REVIEW
                    || current == TemplateModerationStatus.APPROVED) && reason != null;
        };
        if (!allowed) {
            throw new DomainException(ErrorCode.TEMPLATE_REVIEW_NOT_ALLOWED);
        }
    }

    private boolean isSameReviewState(
            TemplateModerationStatus status, TemplateModerationAction action) {
        return (action == TemplateModerationAction.APPROVE
                && status == TemplateModerationStatus.APPROVED)
                || (action == TemplateModerationAction.REJECT
                && status == TemplateModerationStatus.REJECTED)
                || (action == TemplateModerationAction.HIDE
                && status == TemplateModerationStatus.HIDDEN);
    }

    private Map<String, Object> reviewAudit(
            InterviewTemplate template, TemplateModerationAction action) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("action", action);
        values.put("moderationStatus", template.getModerationStatus());
        values.put("reason", template.getModerationReason());
        return values;
    }

    private Map<String, Object> metadataAudit(InterviewTemplate template) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("category", template.getCategory());
        values.put("tagsJson", template.getTagsJson());
        values.put("featured", template.isFeatured());
        values.put("displayOrder", template.getDisplayOrder());
        return values;
    }

    private String tagsJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                normalized.add(tag.strip());
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(normalized));
        } catch (JacksonException exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR,
                    "Cannot serialize template tags", exception);
        }
    }

    private InterviewTemplate requireTemplate(Long id) {
        return templates.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.TEMPLATE_NOT_FOUND));
    }

    private InterviewTemplate locked(Long id) {
        return templates.findByIdForUpdate(id)
                .orElseThrow(() -> new DomainException(ErrorCode.TEMPLATE_NOT_FOUND));
    }

    private void requireVersion(InterviewTemplate template, long expected) {
        if (expected < 0 || template.getVersion() != expected) {
            throw new DomainException(ErrorCode.TEMPLATE_VERSION_CONFLICT);
        }
    }

    private void requireActive(InterviewTemplate template) {
        if (template.getArchivedAt() != null) {
            throw new DomainException(ErrorCode.TEMPLATE_ARCHIVED);
        }
    }

    private AdminUserReferenceResponse user(UserAccount value) {
        return value == null ? null
                : new AdminUserReferenceResponse(value.getId(), value.getFullName(), value.getEmail());
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

    private String exact(String value) {
        return value == null || value.isBlank()
                ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
