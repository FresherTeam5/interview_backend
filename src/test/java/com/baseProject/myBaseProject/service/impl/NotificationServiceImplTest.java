package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.AccountPreference;
import com.baseProject.myBaseProject.entity.UserNotification;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.repository.AccountPreferenceRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.UserNotificationRepository;
import com.baseProject.myBaseProject.service.AccountMailService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {
    @Test
    void alwaysCreatesInAppNotificationButRespectsEmailPreference() {
        Instant now = Instant.parse("2026-09-18T03:00:00Z");
        UserNotificationRepository notifications = mock(UserNotificationRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        AccountPreferenceRepository preferences = mock(AccountPreferenceRepository.class);
        AccountMailService mail = mock(AccountMailService.class);
        UserAccount user = UserAccount.builder()
                .id(7L).email("user@example.com").fullName("User").build();
        AccountPreference preference = AccountPreference.builder()
                .user(user).emailNotifications(false).processingNotifications(true)
                .updatedAt(now).build();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(preferences.findById(7L)).thenReturn(Optional.of(preference));
        when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationServiceImpl service = new NotificationServiceImpl(
                notifications, users, preferences, mail, Clock.fixed(now, ZoneOffset.UTC));

        service.create(7L, UserNotificationType.CV_READY, "CV ready", "Profile ready",
                "CV_DOCUMENT", 11L);

        var captor = org.mockito.ArgumentCaptor.forClass(UserNotification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(UserNotificationType.CV_READY);
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(now);
        verify(mail, never()).sendNotification(any(), any(), any());
    }
}
