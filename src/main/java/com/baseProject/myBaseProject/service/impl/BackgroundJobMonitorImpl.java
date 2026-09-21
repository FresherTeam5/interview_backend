package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.BackgroundJobRun;
import com.baseProject.myBaseProject.enums.BackgroundJobStatus;
import com.baseProject.myBaseProject.enums.BackgroundJobTrigger;
import com.baseProject.myBaseProject.repository.BackgroundJobRunRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.function.IntSupplier;

@Service
public class BackgroundJobMonitorImpl implements BackgroundJobMonitor {
    private final BackgroundJobRunRepository runs;
    private final UserAccountRepository users;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public BackgroundJobMonitorImpl(
            BackgroundJobRunRepository runs,
            UserAccountRepository users,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.runs = runs;
        this.users = users;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public int runScheduled(String jobName, IntSupplier action) {
        return run(jobName, BackgroundJobTrigger.SCHEDULED, null, action);
    }

    @Override
    public int runManual(String jobName, Long adminId, IntSupplier action) {
        return run(jobName, BackgroundJobTrigger.MANUAL, adminId, action);
    }

    private int run(
            String jobName,
            BackgroundJobTrigger trigger,
            Long adminId,
            IntSupplier action) {
        Long runId = transactions.execute(status -> start(jobName, trigger, adminId));
        if (runId == null) {
            throw new IllegalStateException("Background job run was not created");
        }
        try {
            int processed = Math.max(0, action.getAsInt());
            transactions.executeWithoutResult(status -> finish(runId, processed, null));
            return processed;
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> finish(runId, 0, exception));
            throw exception;
        }
    }

    private Long start(
            String jobName, BackgroundJobTrigger trigger, Long adminId) {
        BackgroundJobRun run = new BackgroundJobRun();
        run.setJobName(jobName);
        run.setTrigger(trigger);
        run.setTriggeredBy(adminId == null ? null : users.getReferenceById(adminId));
        run.setStatus(BackgroundJobStatus.RUNNING);
        run.setStartedAt(clock.instant());
        return runs.save(run).getId();
    }

    private void finish(Long runId, int processed, RuntimeException exception) {
        BackgroundJobRun run = runs.findById(runId).orElseThrow();
        run.setStatus(exception == null
                ? BackgroundJobStatus.SUCCEEDED : BackgroundJobStatus.FAILED);
        run.setProcessedCount(processed);
        run.setErrorMessage(exception == null ? null : truncate(
                exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage(), 1000));
        run.setFinishedAt(clock.instant());
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
