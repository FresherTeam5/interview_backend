package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.repository.BackgroundJobRunRepository;
import com.baseProject.myBaseProject.service.SystemSettingService;
import com.baseProject.myBaseProject.service.impl.SystemSettingServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackgroundJobMaintenanceJob {
    private final BackgroundJobRunRepository runs;
    private final SystemSettingService settings;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(
            fixedDelayString = "${app.admin.job-recovery-ms:3600000}",
            initialDelayString = "${app.admin.job-recovery-ms:3600000}")
    @Transactional
    public void recoverInterruptedRuns() {
        int recovered = runs.failInterruptedRuns(
                clock.instant().minus(1, ChronoUnit.HOURS),
                clock.instant(),
                "Application stopped before the job completed");
        if (recovered > 0) {
            log.warn("Marked {} interrupted background job run(s) as failed", recovered);
        }
    }

    @Scheduled(cron = "${app.admin.job-history-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteExpiredHistory() {
        int retentionDays = settings.integerValue(
                SystemSettingServiceImpl.BACKGROUND_JOB_RETENTION_DAYS, 90);
        int deleted = runs.deleteCompletedBefore(
                clock.instant().minus(retentionDays, ChronoUnit.DAYS));
        if (deleted > 0) {
            log.info("Deleted {} expired background job run(s)", deleted);
        }
    }
}
