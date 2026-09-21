package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminTemplateReviewRequest;
import com.baseProject.myBaseProject.dto.template.TemplateVersionRequest;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.TemplateModerationAction;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.mapper.InterviewTemplateMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.TemplateFavoriteRepository;
import com.baseProject.myBaseProject.repository.TemplateViewRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.NotificationService;
import com.baseProject.myBaseProject.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTemplateModerationServiceImplTest {
    @Test
    void rejectingTemplateUnpublishesAndUnlocksItForOwnerEdits() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserAccount owner = UserAccount.builder()
                .id(7L).fullName("Minh").email("minh@example.com").build();
        UserAccount admin = UserAccount.builder()
                .id(3L).fullName("Admin").email("admin@example.com").build();
        InterviewTemplate template = new InterviewTemplate();
        template.setId(21L);
        template.setOwner(owner);
        template.setTitle("Java Backend");
        template.setConfirmedAt(now.minusSeconds(3600));
        template.setPublishedAt(now.minusSeconds(1800));
        template.setModerationStatus(TemplateModerationStatus.PENDING_REVIEW);
        template.setCreatedAt(now.minusSeconds(7200));
        template.setUpdatedAt(now.minusSeconds(60));
        when(templates.findByIdForUpdate(21L)).thenReturn(Optional.of(template));
        when(users.getReferenceById(3L)).thenReturn(admin);

        AdminTemplateModerationServiceImpl service = new AdminTemplateModerationServiceImpl(
                templates, mock(TemplateViewRepository.class),
                mock(TemplateFavoriteRepository.class), mock(InterviewSessionRepository.class),
                users, mock(InterviewTemplateMapper.class), notifications,
                mock(AdminAuditService.class), new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC), mock(SystemSettingService.class));

        service.review(3L, 21L, new AdminTemplateReviewRequest(
                TemplateModerationAction.REJECT, "Remove discriminatory wording", 0));

        assertThat(template.getModerationStatus()).isEqualTo(TemplateModerationStatus.REJECTED);
        assertThat(template.getConfirmedAt()).isNull();
        assertThat(template.getPublishedAt()).isNull();
        verify(notifications).create(eq(7L), eq(UserNotificationType.TEMPLATE_REJECTED),
                eq("Template review: Java Backend"),
                eq("Your interview template needs changes: Remove discriminatory wording"),
                eq("INTERVIEW_TEMPLATE"), eq(21L));
    }

    @Test
    void publishingAndUnpublishingUpdateStateAndWriteAuditEntries() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        AdminAuditService audit = mock(AdminAuditService.class);
        SystemSettingService settings = mock(SystemSettingService.class);
        InterviewTemplate template = new InterviewTemplate();
        template.setId(21L);
        template.setOwner(UserAccount.builder()
                .id(7L).fullName("Minh").email("minh@example.com").build());
        template.setTitle("Java Backend");
        template.setConfirmedAt(now.minusSeconds(3600));
        template.setModerationStatus(TemplateModerationStatus.APPROVED);
        template.setCreatedAt(now.minusSeconds(7200));
        template.setUpdatedAt(now.minusSeconds(60));
        when(templates.findByIdForUpdate(21L)).thenReturn(Optional.of(template));
        when(settings.booleanValue(SystemSettingServiceImpl.TEMPLATE_REVIEW_REQUIRED, true))
                .thenReturn(true);

        AdminTemplateModerationServiceImpl service = new AdminTemplateModerationServiceImpl(
                templates, mock(TemplateViewRepository.class),
                mock(TemplateFavoriteRepository.class), mock(InterviewSessionRepository.class),
                mock(UserAccountRepository.class), mock(InterviewTemplateMapper.class),
                mock(NotificationService.class), audit, new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC), settings);

        service.publish(3L, 21L, new TemplateVersionRequest(0L));
        assertThat(template.getPublishedAt()).isEqualTo(now);
        verify(audit).record(eq(3L), eq(com.baseProject.myBaseProject.enums.AdminAuditAction.TEMPLATE_PUBLISHED),
                eq("INTERVIEW_TEMPLATE"), eq(21L), isNull(), eq(Map.of("published", true)));

        service.unpublish(3L, 21L, new TemplateVersionRequest(0L));
        assertThat(template.getPublishedAt()).isNull();
        verify(audit).record(eq(3L), eq(com.baseProject.myBaseProject.enums.AdminAuditAction.TEMPLATE_UNPUBLISHED),
                eq("INTERVIEW_TEMPLATE"), eq(21L), isNull(), eq(Map.of("published", false)));
    }
}
