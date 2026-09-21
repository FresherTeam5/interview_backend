package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminAddSupportMessageRequest;
import com.baseProject.myBaseProject.dto.admin.AdminInterviewFeedbackResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AssignSupportTicketRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketPriorityRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketStatusRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminSupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/support")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị - Hỗ trợ")
public class AdminSupportController {
    private final AdminSupportService service;

    @GetMapping("/tickets")
    @Operation(summary = "Tìm kiếm yêu cầu hỗ trợ")
    public AdminPageResponse<AdminSupportTicketSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(required = false) SupportTicketType type,
            @RequestParam(required = false) SupportTicketPriority priority,
            @RequestParam(required = false) Long assignedAdminId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, status, type, priority, assignedAdminId,
                from, to, page, size);
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "Lấy chi tiết yêu cầu hỗ trợ")
    public AdminSupportTicketDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/tickets/{id}/assignment")
    @Operation(summary = "Phân công hoặc bỏ phân công yêu cầu hỗ trợ")
    public AdminSupportTicketDetailResponse assign(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @RequestBody AssignSupportTicketRequest request) {
        return service.assign(admin.getId(), id, request);
    }

    @PatchMapping("/tickets/{id}/priority")
    @Operation(summary = "Cập nhật độ ưu tiên yêu cầu hỗ trợ")
    public AdminSupportTicketDetailResponse priority(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody UpdateSupportTicketPriorityRequest request) {
        return service.updatePriority(admin.getId(), id, request);
    }

    @PatchMapping("/tickets/{id}/status")
    @Operation(summary = "Chuyển trạng thái yêu cầu hỗ trợ")
    public AdminSupportTicketDetailResponse status(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody UpdateSupportTicketStatusRequest request) {
        return service.updateStatus(admin.getId(), id, request);
    }

    @PostMapping("/tickets/{id}/messages")
    @Operation(summary = "Thêm phản hồi công khai hoặc ghi chú nội bộ")
    public SupportTicketMessageResponse message(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody AdminAddSupportMessageRequest request) {
        return service.addMessage(admin.getId(), id, request);
    }

    @GetMapping("/feedback")
    @Operation(summary = "Tìm kiếm feedback phỏng vấn")
    public AdminPageResponse<AdminInterviewFeedbackResponse> feedback(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listFeedback(keyword, rating, from, to, page, size);
    }
}
