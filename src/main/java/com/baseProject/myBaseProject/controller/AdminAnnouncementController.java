package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminAnnouncementRequest;
import com.baseProject.myBaseProject.dto.admin.AdminAnnouncementResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AnnouncementAudiencePreviewResponse;
import com.baseProject.myBaseProject.dto.admin.ScheduleAnnouncementRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateAdminAnnouncementRequest;
import com.baseProject.myBaseProject.dto.template.TemplateVersionRequest;
import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/announcements")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị - Thông báo")
public class AdminAnnouncementController {
    private final AdminAnnouncementService service;

    @GetMapping
    @Operation(summary = "Lấy danh sách thông báo hệ thống")
    public AdminPageResponse<AdminAnnouncementResponse> list(
            @RequestParam(required = false) AnnouncementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết và tiến độ gửi thông báo")
    public AdminAnnouncementResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/audience-preview")
    @Operation(summary = "Đếm trước số user đủ điều kiện nhận thông báo")
    public AnnouncementAudiencePreviewResponse preview(
            @RequestParam AnnouncementAudience audience) {
        return service.preview(audience);
    }

    @PostMapping
    @Operation(summary = "Tạo bản nháp thông báo")
    public AdminAnnouncementResponse create(
            @CurrentUser CustomUserDetails admin,
            @Valid @RequestBody AdminAnnouncementRequest request) {
        return service.create(admin.getId(), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cập nhật bản nháp thông báo")
    public AdminAnnouncementResponse update(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody UpdateAdminAnnouncementRequest request) {
        return service.update(admin.getId(), id, request);
    }

    @PostMapping("/{id}/schedule")
    @Operation(summary = "Lên lịch gửi thông báo")
    public AdminAnnouncementResponse schedule(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody ScheduleAnnouncementRequest request) {
        return service.schedule(admin.getId(), id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy bản nháp hoặc thông báo chưa bắt đầu gửi")
    public AdminAnnouncementResponse cancel(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.cancel(admin.getId(), id, request.expectedVersion());
    }
}
