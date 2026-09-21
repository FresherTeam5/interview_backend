package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.ScheduleAnnouncementRequest;
import com.baseProject.myBaseProject.entity.AdminAnnouncement;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;
import com.baseProject.myBaseProject.repository.AdminAnnouncementRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAnnouncementServiceImplTest {
    @Test
    void schedulingDraftCapturesAudienceSizeAndAuditEntry() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        Instant scheduledAt = now.plusSeconds(300);
        AdminAnnouncementRepository announcements = mock(AdminAnnouncementRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AdminAuditService audit = mock(AdminAuditService.class);
        AdminAnnouncement announcement = draft(now);
        when(announcements.findByIdForUpdate(9L)).thenReturn(Optional.of(announcement));
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                eq(Long.class), eq(now))).thenReturn(12L);

        AdminAnnouncementServiceImpl service = new AdminAnnouncementServiceImpl(
                announcements, mock(UserAccountRepository.class), audit, jdbc,
                Clock.fixed(now, ZoneOffset.UTC));

        var response = service.schedule(3L, 9L,
                new ScheduleAnnouncementRequest(scheduledAt, 0L));

        assertThat(response.status()).isEqualTo(AnnouncementStatus.SCHEDULED);
        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
        verify(audit).record(eq(3L), eq(AdminAuditAction.ANNOUNCEMENT_SCHEDULED),
                eq("ANNOUNCEMENT"), eq(9L),
                org.mockito.ArgumentMatchers.isNull(),
                eq(Map.of("scheduledAt", scheduledAt, "eligibleRecipients", 12L)));
    }

    private AdminAnnouncement draft(Instant now) {
        AdminAnnouncement value = new AdminAnnouncement();
        value.setId(9L);
        value.setCreatedBy(UserAccount.builder()
                .id(3L).fullName("Admin").email("admin@example.com").build());
        value.setTitle("Maintenance");
        value.setMessage("Scheduled maintenance");
        value.setAudience(AnnouncementAudience.ALL_USERS);
        value.setInAppEnabled(true);
        value.setStatus(AnnouncementStatus.DRAFT);
        value.setCreatedAt(now.minusSeconds(60));
        value.setUpdatedAt(now.minusSeconds(60));
        return value;
    }
}
