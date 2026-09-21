package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.AdminAnnouncement;
import com.baseProject.myBaseProject.entity.AnnouncementDelivery;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AnnouncementDeliveryStatus;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.repository.AnnouncementDeliveryRepository;
import com.baseProject.myBaseProject.repository.UserNotificationRepository;
import com.baseProject.myBaseProject.service.AccountMailService;
import com.baseProject.myBaseProject.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementDeliveryProcessorTest {
    @Test
    void retryAfterEmailFailureDoesNotCreateDuplicateInAppNotification() {
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        AnnouncementDeliveryRepository deliveries = mock(AnnouncementDeliveryRepository.class);
        UserNotificationRepository userNotifications = mock(UserNotificationRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        AccountMailService mail = mock(AccountMailService.class);
        UserAccount user = UserAccount.builder()
                .id(7L).fullName("Minh").email("minh@example.com").build();
        AdminAnnouncement announcement = new AdminAnnouncement();
        announcement.setId(9L);
        announcement.setTitle("Maintenance");
        announcement.setMessage("Scheduled maintenance");
        announcement.setInAppEnabled(true);
        announcement.setEmailEnabled(true);
        AnnouncementDelivery delivery = new AnnouncementDelivery();
        delivery.setId(15L);
        delivery.setAnnouncement(announcement);
        delivery.setUser(user);
        delivery.setStatus(AnnouncementDeliveryStatus.PENDING);
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        when(deliveries.findByIdForUpdate(15L)).thenReturn(Optional.of(delivery));
        when(userNotifications.existsByUserIdAndTypeAndResourceTypeAndResourceId(
                7L, UserNotificationType.ADMIN_ANNOUNCEMENT, "ANNOUNCEMENT", 9L))
                .thenReturn(false, true);
        doThrow(new IllegalStateException("mail unavailable"))
                .doNothing()
                .when(mail).sendNotification("minh@example.com", "Maintenance",
                        "Scheduled maintenance");

        AnnouncementDeliveryProcessor processor = new AnnouncementDeliveryProcessor(
                deliveries, userNotifications, notifications, mail,
                Clock.fixed(now, ZoneOffset.UTC));

        processor.process(15L);
        assertThat(delivery.getStatus()).isEqualTo(AnnouncementDeliveryStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(1);

        processor.process(15L);
        assertThat(delivery.getStatus()).isEqualTo(AnnouncementDeliveryStatus.DELIVERED);
        assertThat(delivery.getAttempts()).isEqualTo(2);
        verify(notifications, times(1)).createInAppOnly(7L,
                UserNotificationType.ADMIN_ANNOUNCEMENT, "Maintenance",
                "Scheduled maintenance", "ANNOUNCEMENT", 9L);
        verify(mail, times(2)).sendNotification("minh@example.com", "Maintenance",
                "Scheduled maintenance");
    }
}
