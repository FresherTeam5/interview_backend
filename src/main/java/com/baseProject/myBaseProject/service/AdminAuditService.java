package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminAuditLogResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.enums.AdminAuditAction;

import java.time.Instant;

public interface AdminAuditService {
    void record(
            Long actorId,
            AdminAuditAction action,
            String resourceType,
            Object resourceId,
            Object before,
            Object after);

    AdminPageResponse<AdminAuditLogResponse> list(
            Long actorId,
            AdminAuditAction action,
            String resourceType,
            String resourceId,
            Instant from,
            Instant to,
            int page,
            int size);
}
