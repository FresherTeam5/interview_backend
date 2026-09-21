package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AdminAuditAction;

import java.time.Instant;

public record AdminAuditLogResponse(
        Long id,
        AdminUserReferenceResponse actor,
        AdminAuditAction action,
        String resourceType,
        String resourceId,
        String beforeJson,
        String afterJson,
        String requestId,
        String ipAddress,
        Instant createdAt) {
}
