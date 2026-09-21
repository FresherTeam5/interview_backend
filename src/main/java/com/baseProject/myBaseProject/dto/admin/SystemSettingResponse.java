package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.SystemSettingType;

import java.time.Instant;

public record SystemSettingResponse(
        String key,
        String value,
        SystemSettingType type,
        String description,
        long version,
        AdminUserReferenceResponse updatedBy,
        Instant updatedAt) {
}
