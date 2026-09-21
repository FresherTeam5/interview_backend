package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.BackgroundJobStatus;
import com.baseProject.myBaseProject.enums.BackgroundJobTrigger;

import java.time.Instant;

public record BackgroundJobRunResponse(
        Long id,
        String jobName,
        BackgroundJobTrigger trigger,
        AdminUserReferenceResponse triggeredBy,
        BackgroundJobStatus status,
        int processedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {
}
