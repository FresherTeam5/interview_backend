package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.notification.NotificationPageResponse;
import com.baseProject.myBaseProject.dto.notification.NotificationResponse;
import com.baseProject.myBaseProject.entity.AccountPreference;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.entity.UserNotification;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.AccountPreferenceRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.UserNotificationRepository;
import com.baseProject.myBaseProject.service.AccountMailService;
import com.baseProject.myBaseProject.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final UserNotificationRepository notifications;
    private final UserAccountRepository users;
    private final AccountPreferenceRepository preferences;
    private final AccountMailService mail;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long userId, UserNotificationType type, String title, String message,
                       String resourceType, Long resourceId) {
        UserAccount user = users.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setResourceType(resourceType);
        notification.setResourceId(resourceId);
        notification.setCreatedAt(clock.instant());
        notifications.save(notification);

        AccountPreference preference = preferences.findById(userId).orElse(null);
        boolean emailEnabled = preference == null || preference.isEmailNotifications();
        boolean processingEnabled = preference == null || preference.isProcessingNotifications();
        if (emailEnabled && processingEnabled) {
            try {
                mail.sendNotification(user.getEmail(), title, message);
            } catch (RuntimeException exception) {
                log.warn("Cannot send notification email to userId={}", userId, exception);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long userId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<UserNotification> result = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNull(userId, pageable)
                : notifications.findByUserId(userId, pageable);
        return new NotificationPageResponse(result.map(this::map).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        notifications.markRead(userId, notificationId, clock.instant());
        UserNotification notification = notifications.findById(notificationId)
                .filter(value -> value.getUser().getId().equals(userId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOTIFICATION_NOT_FOUND));
        return map(notification);
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, clock.instant());
    }

    private NotificationResponse map(UserNotification value) {
        return new NotificationResponse(value.getId(), value.getType(), value.getTitle(),
                value.getMessage(), value.getResourceType(), value.getResourceId(),
                value.getReadAt(), value.getCreatedAt());
    }
}
