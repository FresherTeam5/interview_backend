package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AccountDeletionTaskResponse;
import com.baseProject.myBaseProject.dto.admin.AdminActionCountResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.BackgroundJobRunResponse;
import com.baseProject.myBaseProject.dto.admin.StorageDeletionTaskResponse;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.BackgroundJobStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/operations")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị - Vận hành")
public class AdminOperationsController {
    private final AdminOperationsService service;

    @GetMapping("/job-runs")
    @Operation(summary = "Lấy lịch sử chạy background job")
    public AdminPageResponse<BackgroundJobRunResponse> jobRuns(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) BackgroundJobStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.jobRuns(jobName, status, from, to, page, size);
    }

    @PostMapping("/jobs/{jobName}/run")
    @Operation(summary = "Chạy thủ công một background job được cho phép")
    public AdminActionCountResponse runJob(
            @CurrentUser CustomUserDetails admin, @PathVariable String jobName) {
        return new AdminActionCountResponse(service.runJob(admin.getId(), jobName));
    }

    @GetMapping("/storage-deletions")
    @Operation(summary = "Lấy queue xóa object trong storage")
    public AdminPageResponse<StorageDeletionTaskResponse> storage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.storageDeletions(page, size);
    }

    @PostMapping("/storage-deletions/{id}/retry")
    @Operation(summary = "Đưa tác vụ xóa storage về trạng thái sẵn sàng retry")
    public ResponseEntity<Void> retryStorage(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id) {
        service.retryStorageDeletion(admin.getId(), id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/account-deletions")
    @Operation(summary = "Lấy queue yêu cầu xóa tài khoản")
    public AdminPageResponse<AccountDeletionTaskResponse> accountDeletions(
            @RequestParam(required = false) AccountDeletionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.accountDeletions(status, page, size);
    }

    @PostMapping("/account-deletions/{id}/retry")
    @Operation(summary = "Đưa yêu cầu xóa tài khoản thất bại vào queue retry")
    public ResponseEntity<Void> retryAccount(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id) {
        service.retryAccountDeletion(admin.getId(), id);
        return ResponseEntity.accepted().build();
    }
}
