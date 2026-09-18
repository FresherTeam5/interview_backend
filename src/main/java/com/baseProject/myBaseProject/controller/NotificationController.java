package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.notification.NotificationPageResponse;
import com.baseProject.myBaseProject.dto.notification.NotificationResponse;
import com.baseProject.myBaseProject.dto.notification.UnreadNotificationCountResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Thông báo")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Lấy danh sách thông báo")
    public NotificationPageResponse list(
            @CurrentUser CustomUserDetails user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user.getId(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm thông báo chưa đọc")
    public UnreadNotificationCountResponse unreadCount(@CurrentUser CustomUserDetails user) {
        return new UnreadNotificationCountResponse(service.unreadCount(user.getId()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public NotificationResponse markRead(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.markRead(user.getId(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Đánh dấu mọi thông báo đã đọc")
    public UnreadNotificationCountResponse markAllRead(@CurrentUser CustomUserDetails user) {
        service.markAllRead(user.getId());
        return new UnreadNotificationCountResponse(0);
    }
}
