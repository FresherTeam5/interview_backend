package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AccountDeletionTaskResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.BackgroundJobRunResponse;
import com.baseProject.myBaseProject.dto.admin.StorageDeletionTaskResponse;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.BackgroundJobStatus;

import java.time.Instant;

public interface AdminOperationsService {
    AdminPageResponse<BackgroundJobRunResponse> jobRuns(
            String jobName, BackgroundJobStatus status, Instant from, Instant to,
            int page, int size);

    int runJob(Long adminId, String jobName);

    AdminPageResponse<StorageDeletionTaskResponse> storageDeletions(int page, int size);

    void retryStorageDeletion(Long adminId, Long taskId);

    AdminPageResponse<AccountDeletionTaskResponse> accountDeletions(
            AccountDeletionStatus status, int page, int size);

    void retryAccountDeletion(Long adminId, Long requestId);
}
