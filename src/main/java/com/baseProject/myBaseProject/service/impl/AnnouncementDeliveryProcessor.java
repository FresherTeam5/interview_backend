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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementDeliveryProcessor {
    private static final int MAX_ATTEMPTS = 3;

    private final AnnouncementDeliveryRepository deliveries;
    private final UserNotificationRepository userNotifications;
    private final NotificationService notifications;
    private final AccountMailService mail;
    private final Clock clock;

    @Transactional
    public void process(Long deliveryId) {
        AnnouncementDelivery delivery = deliveries.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null || delivery.getStatus() == AnnouncementDeliveryStatus.DELIVERED
                || delivery.getAttempts() >= MAX_ATTEMPTS) {
            return;
        }
        Instant now = clock.instant();
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setUpdatedAt(now);
        try {
            AdminAnnouncement announcement = delivery.getAnnouncement();
            UserAccount user = delivery.getUser();
            if (announcement.isInAppEnabled()
                    && !userNotifications.existsByUserIdAndTypeAndResourceTypeAndResourceId(
                    user.getId(), UserNotificationType.ADMIN_ANNOUNCEMENT,
                    "ANNOUNCEMENT", announcement.getId())) {
                notifications.createInAppOnly(user.getId(),
                        UserNotificationType.ADMIN_ANNOUNCEMENT,
                        announcement.getTitle(), announcement.getMessage(),
                        "ANNOUNCEMENT", announcement.getId());
            }
            if (announcement.isEmailEnabled()) {
                mail.sendNotification(user.getEmail(), announcement.getTitle(),
                        announcement.getMessage());
            }
            delivery.setStatus(AnnouncementDeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(now);
            delivery.setLastError(null);
        } catch (RuntimeException exception) {
            delivery.setStatus(AnnouncementDeliveryStatus.FAILED);
            delivery.setLastError(truncate(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage(), 1000));
            log.warn("Cannot deliver announcement deliveryId={}, attempt={}",
                    deliveryId, delivery.getAttempts(), exception);
        }
    }

    private String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
