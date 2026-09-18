package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewProgressResponse;
import com.baseProject.myBaseProject.dto.session.InterviewReadinessResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionPageResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import com.baseProject.myBaseProject.service.InterviewHistoryService;
import com.baseProject.myBaseProject.service.InterviewReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/interview-sessions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Phiên phỏng vấn")
public class InterviewSessionController {
    private final InterviewSessionService service;
    private final InterviewHistoryService historyService;
    private final InterviewReadinessService readinessService;

    @GetMapping
    @Operation(
            summary = "Lấy lịch sử phỏng vấn",
            description = "Tìm kiếm, lọc và phân trang các phiên thuộc người dùng hiện tại.")
    public InterviewSessionPageResponse list(
            @CurrentUser CustomUserDetails user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InterviewSessionStatus status,
            @RequestParam(required = false) InterviewSessionMode mode,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return historyService.list(
                user.getId(), keyword, status, mode, createdFrom, createdTo, page, size);
    }

    @GetMapping("/progress")
    @Operation(
            summary = "Lấy tiến bộ phỏng vấn",
            description = "Tổng hợp tỷ lệ hoàn thành, điểm, xu hướng và các nhóm kỹ năng yếu.")
    public InterviewProgressResponse progress(
            @CurrentUser CustomUserDetails user,
            @RequestParam(defaultValue = "30") int days) {
        return historyService.progress(user.getId(), days);
    }

    @PostMapping("/readiness")
    @Operation(
            summary = "Kiểm tra khả năng bắt đầu phỏng vấn",
            description = "Kiểm tra profile, template, option và dịch vụ voice trước khi tạo session.")
    public InterviewReadinessResponse readiness(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody CreateInterviewSessionRequest request) {
        return readinessService.check(user.getId(), request);
    }

    @PostMapping
    @Operation(
            summary = "Tạo phiên phỏng vấn",
            description = "Tạo phiên từ mẫu và hồ sơ đã chọn, sau đó chuẩn bị kế hoạch phỏng vấn ở chế độ nền.")
    public ResponseEntity<InterviewSessionStatusResponse> create(
            @CurrentUser CustomUserDetails user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateInterviewSessionRequest request) {
        return ResponseEntity.accepted()
                .body(service.create(user.getId(), idempotencyKey, request));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy trạng thái phiên phỏng vấn",
            description = "Trả về thông tin và trạng thái chuẩn bị hiện tại của một phiên phỏng vấn.")
    public InterviewSessionStatusResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PostMapping("/{id}/preparation/retry")
    @Operation(
            summary = "Thử lại chuẩn bị phỏng vấn",
            description = "Khởi động lại bước chuẩn bị kế hoạch cho phiên phỏng vấn đã chuẩn bị thất bại.")
    public ResponseEntity<InterviewSessionStatusResponse> retryPreparation(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.accepted()
                .body(service.retryPreparation(user.getId(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Hủy phiên chưa bắt đầu",
            description = "Hủy idempotent một phiên đang chuẩn bị, đã sẵn sàng hoặc chuẩn bị thất bại.")
    public InterviewSessionStatusResponse cancel(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.cancel(user.getId(), id);
    }
}
