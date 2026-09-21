package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AccountDeletionTaskResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.dto.admin.BackgroundJobRunResponse;
import com.baseProject.myBaseProject.dto.admin.StorageDeletionTaskResponse;
import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import com.baseProject.myBaseProject.entity.BackgroundJobRun;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.BackgroundJobStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewDeadlineService;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.BackgroundJobRunRepository;
import com.baseProject.myBaseProject.service.AccountCredentialService;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.AdminOperationsService;
import com.baseProject.myBaseProject.service.AnnouncementDispatchService;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationsServiceImpl implements AdminOperationsService {
    private static final Set<String> MANUAL_JOBS = Set.of(
            "ACCOUNT_TOKEN_CLEANUP",
            "REFRESH_TOKEN_CLEANUP",
            "INTERVIEW_DEADLINE",
            "ANNOUNCEMENT_DISPATCH");

    private final BackgroundJobRunRepository jobRuns;
    private final AccountDeletionRequestRepository accountDeletions;
    private final AccountCredentialService credentials;
    private final RefreshTokenService refreshTokens;
    private final InterviewDeadlineService interviewDeadlines;
    private final AnnouncementDispatchService announcementDispatch;
    private final BackgroundJobMonitor jobMonitor;
    private final AdminAuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Override
    public AdminPageResponse<BackgroundJobRunResponse> jobRuns(
            String rawJobName, BackgroundJobStatus status, Instant from, Instant to,
            int page, int size) {
        validateRange(from, to);
        String jobName = normalizeJobName(rawJobName);
        Page<BackgroundJobRun> result = jobRuns.search(jobName, status, from, to,
                pageRequest(page, size, "startedAt"));
        return new AdminPageResponse<>(result.getContent().stream().map(this::jobRun).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int runJob(Long adminId, String rawJobName) {
        String jobName = normalizeJobName(rawJobName);
        if (jobName == null || !MANUAL_JOBS.contains(jobName)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Unsupported manual job. Allowed: " + String.join(", ", MANUAL_JOBS));
        }
        audit.record(adminId, AdminAuditAction.BACKGROUND_JOB_TRIGGERED,
                "BACKGROUND_JOB", jobName, null, Map.of("trigger", "MANUAL"));
        return jobMonitor.runManual(jobName, adminId, () -> switch (jobName) {
            case "ACCOUNT_TOKEN_CLEANUP" -> credentials.purgeExpiredTokens();
            case "REFRESH_TOKEN_CLEANUP" -> refreshTokens.purgeExpired();
            case "INTERVIEW_DEADLINE" -> interviewDeadlines.closeExpiredSessions();
            case "ANNOUNCEMENT_DISPATCH" -> announcementDispatch.dispatchDue();
            default -> 0;
        });
    }

    @Override
    public AdminPageResponse<StorageDeletionTaskResponse> storageDeletions(
            int page, int size) {
        validatePage(page, size);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM storage_deletion_tasks", Long.class);
        var content = jdbc.query("""
                SELECT id, storage_key, attempts, last_error, next_attempt_at, created_at
                FROM storage_deletion_tasks
                ORDER BY next_attempt_at ASC, id ASC
                LIMIT ? OFFSET ?
                """, (result, row) -> new StorageDeletionTaskResponse(
                        result.getLong("id"), result.getString("storage_key"),
                        result.getInt("attempts"), result.getString("last_error"),
                        instant(result.getTimestamp("next_attempt_at")),
                        instant(result.getTimestamp("created_at"))),
                size, (long) page * size);
        return new AdminPageResponse<>(content, page, size, total == null ? 0 : total);
    }

    @Override
    @Transactional
    public void retryStorageDeletion(Long adminId, Long taskId) {
        int updated = jdbc.update("""
                UPDATE storage_deletion_tasks
                SET next_attempt_at = ?, last_error = NULL
                WHERE id = ?
                """, clock.instant(), taskId);
        if (updated == 0) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Storage deletion task not found");
        }
        audit.record(adminId, AdminAuditAction.STORAGE_DELETION_RETRIED,
                "STORAGE_DELETION_TASK", taskId, null, Map.of("queued", true));
    }

    @Override
    public AdminPageResponse<AccountDeletionTaskResponse> accountDeletions(
            AccountDeletionStatus status, int page, int size) {
        PageRequest pageable = pageRequest(page, size, "scheduledAt");
        Page<AccountDeletionRequest> result = status == null
                ? accountDeletions.findAll(pageable)
                : accountDeletions.findByStatus(status, pageable);
        return new AdminPageResponse<>(result.getContent().stream()
                .map(this::accountDeletion).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional
    public void retryAccountDeletion(Long adminId, Long requestId) {
        AccountDeletionRequest request = accountDeletions.findByIdForUpdate(requestId)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Account deletion request not found"));
        if (request.getStatus() != AccountDeletionStatus.PENDING
                || request.getLastError() == null
                || request.getScheduledAt().isAfter(clock.instant())) {
            throw new DomainException(ErrorCode.ACCOUNT_DELETION_NOT_PENDING,
                    "Only a due, failed account deletion can be retried");
        }
        request.setNextAttemptAt(clock.instant());
        request.setLastError(null);
        audit.record(adminId, AdminAuditAction.ACCOUNT_DELETION_RETRIED,
                "ACCOUNT_DELETION_REQUEST", requestId, null, Map.of("queued", true));
    }

    private BackgroundJobRunResponse jobRun(BackgroundJobRun value) {
        UserAccount admin = value.getTriggeredBy();
        AdminUserReferenceResponse triggeredBy = admin == null ? null
                : new AdminUserReferenceResponse(admin.getId(), admin.getFullName(), admin.getEmail());
        return new BackgroundJobRunResponse(value.getId(), value.getJobName(),
                value.getTrigger(), triggeredBy, value.getStatus(), value.getProcessedCount(),
                value.getErrorMessage(), value.getStartedAt(), value.getFinishedAt());
    }

    private AccountDeletionTaskResponse accountDeletion(AccountDeletionRequest value) {
        UserAccount user = value.getUser();
        return new AccountDeletionTaskResponse(value.getId(),
                new AdminUserReferenceResponse(user.getId(), user.getFullName(), user.getEmail()),
                value.getStatus(), value.getRequestedAt(), value.getScheduledAt(),
                value.getCancelledAt(), value.getAttempts(), value.getLastAttemptAt(),
                value.getNextAttemptAt(), value.getLastError());
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String normalizeJobName(String value) {
        return value == null || value.isBlank()
                ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "from must be before or equal to to");
        }
    }

    private PageRequest pageRequest(int page, int size, String sort) {
        validatePage(page, size);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort, "id"));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
