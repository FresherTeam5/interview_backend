package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.TemplatePageResponse;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.CloneInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.TemplateFavoriteResponse;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.jobdescription.validation.JobAnalysisValidator;
import com.baseProject.myBaseProject.mapper.InterviewTemplateMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.TemplateFavoriteRepository;
import com.baseProject.myBaseProject.repository.TemplateViewRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.InterviewTemplateService;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewTemplateServiceImpl implements InterviewTemplateService {
    private static final int EXPORT_PAGE_SIZE = 100;

    private final InterviewTemplateRepository templates;
    private final UserAccountRepository users;
    private final TemplateFavoriteRepository favorites;
    private final TemplateViewRepository views;
    private final InterviewTemplateMapper mapper;
    private final JobAnalysisJsonMapper analysisJsonMapper;
    private final JobAnalysisValidator validator;
    private final Clock clock;

    @Autowired(required = false)
    private SystemSettingService systemSettings;

    @Autowired(required = false)
    private AdminAuditService audit;

    @Override
    @Transactional
    public InterviewTemplateResponse get(Long userId, Long id) {
        InterviewTemplate template = templates.findByIdAndOwnerId(id, userId).orElse(null);
        if (template == null) {
            template = templates.findByIdAndPublishedAtIsNotNullAndArchivedAtIsNull(id)
                    .orElseThrow(this::notFound);
        }

        views.recordView(userId, template.getId(), clock.instant());
        return mapper.toResponse(template);
    }

    @Override
    public TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            Long userId, String scope, int page, int size) {
        return list(userId, scope, null, null, null, null, page, size);
    }

    @Override
    public TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            Long userId, String scope, String keyword, String seniority,
            String language, String technology, int page, int size) {
        String keywordFilter = contains(keyword);
        String seniorityFilter = exact(seniority);
        String languageFilter = jsonValue("sourceLanguage", language);
        String technologyFilter = jsonValue("name", technology);
        PageRequest pageable = pageRequest(page, size);
        Page<InterviewTemplate> result = switch (scope) {
            case "mine" -> templates.searchMine(userId, keywordFilter, seniorityFilter,
                    languageFilter, technologyFilter, pageable);
            case "public" -> templates.searchPublic(keywordFilter, seniorityFilter,
                    languageFilter, technologyFilter, publicPageRequest(page, size));
            case "favorites" -> favorites.searchFavorites(userId, keywordFilter, seniorityFilter,
                    languageFilter, technologyFilter, pageable);
            case "recent" -> views.searchRecent(userId, keywordFilter, seniorityFilter,
                    languageFilter, technologyFilter,
                    PageRequest.of(page, size));
            default -> throw new DomainException(ErrorCode.VALIDATION_FAILED);
        };
        return page(result.map(mapper::toSummary));
    }

    @Override
    public List<InterviewTemplateResponse> listOwnedDetails(Long userId) {
        List<InterviewTemplateResponse> result = new ArrayList<>();
        int page = 0;
        Page<InterviewTemplate> templatesPage;
        do {
            templatesPage = templates.findByOwnerId(userId, PageRequest.of(
                    page++, EXPORT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
            templatesPage.map(mapper::toResponse).forEach(result::add);
        } while (templatesPage.hasNext());
        return List.copyOf(result);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse cloneTemplate(
            Long userId, Long id, CloneInterviewTemplateRequest request) {
        InterviewTemplate source = accessible(userId, id);
        Instant now = clock.instant();
        String requestedTitle = request == null ? null : request.title();
        String title = requestedTitle == null || requestedTitle.isBlank()
                ? defaultCloneTitle(source.getTitle()) : normalizeTitle(requestedTitle);

        InterviewTemplate clone = new InterviewTemplate();
        clone.setOwner(users.getReferenceById(userId));
        clone.setSourceJobDescription(null);
        clone.setTitle(title);
        clone.setJobTitle(source.getJobTitle());
        clone.setTargetSeniority(source.getTargetSeniority());
        clone.setContentJson(source.getContentJson());
        clone.setContentSchemaVersion(source.getContentSchemaVersion());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        return mapper.toResponse(templates.saveAndFlush(clone));
    }

    @Override
    @Transactional
    public TemplateFavoriteResponse favorite(Long userId, Long id) {
        InterviewTemplate template = accessible(userId, id);
        favorites.addForUser(userId, template.getId(), clock.instant());
        return new TemplateFavoriteResponse(id, true);
    }

    @Override
    @Transactional
    public TemplateFavoriteResponse unfavorite(Long userId, Long id) {
        favorites.deleteForUser(userId, id);
        return new TemplateFavoriteResponse(id, false);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse update(
            Long userId, Long id, UpdateInterviewTemplateRequest request) {
        if (request == null || request.content() == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        requireDraft(template);
        requireVersion(template, requireExpectedVersion(request.expectedVersion()));
        String title = normalizeTitle(request.title());
        var content = validator.validate(request.content());

        template.setTitle(title);
        template.setJobTitle(content.jobTitle());
        template.setTargetSeniority(content.targetSeniority());
        template.setContentJson(analysisJsonMapper.toJson(content));
        if (template.getModerationStatus() == TemplateModerationStatus.REJECTED) {
            template.setModerationStatus(TemplateModerationStatus.DRAFT);
            template.setSubmittedAt(null);
            template.setModerationReason(null);
            template.setReviewedAt(null);
            template.setReviewedBy(null);
        }
        touch(template);
        templates.flush();

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse submitForReview(
            Long userId, Long id, long expectedVersion) {
        InterviewTemplate template = ownedForUpdate(userId, id);
        requireActive(template);
        requireConfirmed(template);
        if (template.getModerationStatus() == TemplateModerationStatus.PENDING_REVIEW) {
            return mapper.toResponse(template);
        }
        if (template.getModerationStatus() == TemplateModerationStatus.APPROVED
                || template.getModerationStatus() == TemplateModerationStatus.HIDDEN) {
            throw new DomainException(ErrorCode.TEMPLATE_REVIEW_NOT_ALLOWED);
        }
        requireVersion(template, expectedVersion);
        Instant now = clock.instant();
        template.setModerationStatus(TemplateModerationStatus.PENDING_REVIEW);
        template.setSubmittedAt(now);
        template.setReviewedAt(null);
        template.setReviewedBy(null);
        template.setModerationReason(null);
        template.setUpdatedAt(now);
        templates.flush();
        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse confirm(
            Long userId, Long id, long expectedVersion) {
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        if (template.isConfirmed()) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);
        Instant now = clock.instant();

        template.setConfirmedAt(now);
        template.setUpdatedAt(now);
        templates.flush();

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse publish(
            Long userId, Long id, long expectedVersion) {
        requireAdmin(userId);
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        requireConfirmed(template);
        boolean reviewRequired = systemSettings == null || systemSettings.booleanValue(
                SystemSettingServiceImpl.TEMPLATE_REVIEW_REQUIRED, true);
        if (reviewRequired
                && template.getModerationStatus() != TemplateModerationStatus.APPROVED) {
            throw new DomainException(ErrorCode.TEMPLATE_APPROVAL_REQUIRED);
        }
        if (template.isPublished()) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);
        Instant now = clock.instant();

        template.setPublishedAt(now);
        template.setUpdatedAt(now);
        templates.flush();
        recordAudit(userId, AdminAuditAction.TEMPLATE_PUBLISHED, id, true);

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse unpublish(Long userId, Long id, long expectedVersion) {
        requireAdmin(userId);
        InterviewTemplate template = ownedForUpdate(userId, id);

        requireActive(template);
        if (!template.isPublished()) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);

        template.setPublishedAt(null);
        touch(template);
        templates.flush();
        recordAudit(userId, AdminAuditAction.TEMPLATE_UNPUBLISHED, id, false);

        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public InterviewTemplateResponse archive(Long userId, Long id, long expectedVersion) {
        InterviewTemplate template = ownedForUpdate(userId, id);

        if (template.getArchivedAt() != null) {
            return mapper.toResponse(template);
        }
        requireVersion(template, expectedVersion);

        template.setArchivedAt(clock.instant());
        template.setPublishedAt(null);
        template.setFeatured(false);
        touch(template);
        templates.flush();

        return mapper.toResponse(template);
    }

    // helper

    private InterviewTemplate ownedForUpdate(Long userId, Long id) {
        return templates.findOwnedForUpdate(id, userId).orElseThrow(this::notFound);
    }

    private void recordAudit(
            Long actorId, AdminAuditAction action, Long templateId, boolean published) {
        if (audit != null) {
            audit.record(actorId, action, "INTERVIEW_TEMPLATE", templateId, null,
                    Map.of("published", published));
        }
    }

    private InterviewTemplate accessible(Long userId, Long id) {
        return templates.findAccessibleForSession(id, userId).orElseThrow(this::notFound);
    }

    private void requireAdmin(Long userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (!user.isEnabled() || user.getRole() != UserRole.ADMIN) {
            throw new DomainException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireActive(InterviewTemplate template) {
        if (template.getArchivedAt() != null) {
            throw new DomainException(ErrorCode.TEMPLATE_ARCHIVED);
        }
    }

    private void requireDraft(InterviewTemplate template) {
        if (template.isConfirmed()) {
            throw new DomainException(ErrorCode.TEMPLATE_ALREADY_CONFIRMED);
        }
    }

    private void requireConfirmed(InterviewTemplate template) {
        if (!template.isConfirmed()) {
            throw new DomainException(ErrorCode.TEMPLATE_CONFIRM_REQUIRED);
        }
    }

    private void requireVersion(InterviewTemplate template, long expectedVersion) {
        if (expectedVersion < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        if (template.getVersion() != expectedVersion) {
            throw new DomainException(ErrorCode.TEMPLATE_VERSION_CONFLICT);
        }
    }

    private long requireExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return expectedVersion;
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return value.strip();
    }

    private String defaultCloneTitle(String sourceTitle) {
        String title = "Copy of " + sourceTitle;
        return title.length() <= 200 ? title : title.substring(0, 200);
    }

    private String contains(String value) {
        String normalized = exact(value);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private String exact(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private String jsonValue(String key, String value) {
        String normalized = exact(value);
        return normalized == null ? null : "%\"" + key.toLowerCase(java.util.Locale.ROOT)
                + "\":\"" + normalized + "\"%";
    }

    private void touch(InterviewTemplate template) {
        Instant now = clock.instant();
        template.setUpdatedAt(now.isAfter(template.getUpdatedAt())
                ? now : template.getUpdatedAt().plusNanos(1000));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private PageRequest publicPageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("featured"),
                Sort.Order.asc("displayOrder"),
                Sort.Order.desc("publishedAt"),
                Sort.Order.desc("id")));
    }

    private <T> TemplatePageResponse<T> page(Page<T> page) {
        return new TemplatePageResponse<>(page.getContent(), page.getNumber(),
                page.getSize(), page.getTotalElements());
    }

    private DomainException notFound() {
        return new DomainException(ErrorCode.TEMPLATE_NOT_FOUND);
    }
}
