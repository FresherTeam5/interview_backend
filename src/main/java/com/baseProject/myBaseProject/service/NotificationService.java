package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.notification.NotificationPageResponse;
import com.baseProject.myBaseProject.dto.notification.NotificationResponse;
import com.baseProject.myBaseProject.enums.UserNotificationType;

public interface NotificationService {
    void create(Long userId, UserNotificationType type, String title, String message,
                String resourceType, Long resourceId);

    void createInAppOnly(Long userId, UserNotificationType type, String title, String message,
                         String resourceType, Long resourceId);

    NotificationPageResponse list(Long userId, boolean unreadOnly, int page, int size);

    long unreadCount(Long userId);

    NotificationResponse markRead(Long userId, Long notificationId);

    int markAllRead(Long userId);
}
