package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.service.AccountDeletionPurgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionJob {
    private final AccountDeletionRequestRepository requests;
    private final AccountDeletionPurgeService purgeService;
    private final Clock clock;

    @Scheduled(cron = "${app.account.deletion-cron}")
    public void purgeDueAccounts() {
        Instant now = clock.instant();
        List<Long> ids = requests
                .findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        AccountDeletionStatus.PENDING, now)
                .stream()
                .map(request -> request.getId())
                .toList();
        for (Long id : ids) {
            try {
                purgeService.purgeDueRequest(id, now);
            } catch (RuntimeException exception) {
                log.error("Cannot purge account deletion request id={}", id, exception);
            }
        }
    }
}
