package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.SystemSettingResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateSystemSettingRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system-settings")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị - Cấu hình")
public class AdminSystemSettingController {
    private final SystemSettingService service;

    @GetMapping
    @Operation(summary = "Lấy các cấu hình runtime được phép thay đổi")
    public List<SystemSettingResponse> list() {
        return service.list();
    }

    @PatchMapping("/{key}")
    @Operation(summary = "Cập nhật cấu hình theo version")
    public SystemSettingResponse update(
            @CurrentUser CustomUserDetails admin,
            @PathVariable String key,
            @Valid @RequestBody UpdateSystemSettingRequest request) {
        return service.update(admin.getId(), key, request);
    }
}
