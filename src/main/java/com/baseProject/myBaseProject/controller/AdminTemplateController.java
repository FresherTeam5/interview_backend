package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateMetadataRequest;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateReviewRequest;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.TemplateVersionRequest;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminTemplateModerationService;
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
@RequestMapping("/api/admin/templates")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị - Template")
public class AdminTemplateController {
    private final AdminTemplateModerationService service;

    @GetMapping
    @Operation(summary = "Tìm kiếm toàn bộ template")
    public AdminPageResponse<AdminTemplateSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) TemplateModerationStatus status,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, ownerId, status, published, featured, category, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết template và số liệu sử dụng")
    public AdminTemplateDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Duyệt, từ chối hoặc ẩn template")
    public AdminTemplateDetailResponse review(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody AdminTemplateReviewRequest request) {
        return service.review(admin.getId(), id, request);
    }

    @PatchMapping("/{id}/metadata")
    @Operation(summary = "Cập nhật category, tags và vị trí nổi bật")
    public AdminTemplateDetailResponse metadata(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody AdminTemplateMetadataRequest request) {
        return service.updateMetadata(admin.getId(), id, request);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Công khai template đã được duyệt")
    public AdminTemplateDetailResponse publish(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.publish(admin.getId(), id, request);
    }

    @PostMapping("/{id}/unpublish")
    @Operation(summary = "Gỡ công khai template")
    public AdminTemplateDetailResponse unpublish(
            @CurrentUser CustomUserDetails admin, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.unpublish(admin.getId(), id, request);
    }
}
