package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.service.AccountDeletionPurgeService;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionJob {
    private final AccountDeletionRequestRepository requests;
    private final AccountDeletionPurgeService purgeService;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final BackgroundJobMonitor jobs;

    @Scheduled(cron = "${app.account.deletion-cron}")
    public void purgeDueAccounts() {
        jobs.runScheduled("ACCOUNT_DELETION", this::purgeBatch);
    }

    private int purgeBatch() {
        Instant now = clock.instant();
        List<Long> ids = requests
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        AccountDeletionStatus.PENDING, now)
                .stream()
                .map(request -> request.getId())
                .toList();
        for (Long id : ids) {
            try {
                purgeService.purgeDueRequest(id, now);
            } catch (RuntimeException exception) {
                log.error("Cannot purge account deletion request id={}", id, exception);
                recordFailure(id, exception);
            }
        }
        return ids.size();
    }

    private void recordFailure(Long id, RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE account_deletion_requests
                SET attempts = attempts + 1,
                    last_attempt_at = ?,
                    next_attempt_at = DATE_ADD(?, INTERVAL LEAST(60, POW(2, LEAST(attempts + 1, 6))) MINUTE),
                    last_error = ?
                WHERE id = ? AND status = 'PENDING'
                """, now, now, message, id);
    }
}
