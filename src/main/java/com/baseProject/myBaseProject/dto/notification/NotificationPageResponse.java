package com.baseProject.myBaseProject.dto.notification;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements) {
}
