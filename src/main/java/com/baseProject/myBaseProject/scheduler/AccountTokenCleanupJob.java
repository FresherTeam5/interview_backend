package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.service.AccountCredentialService;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountTokenCleanupJob {
    private final AccountCredentialService credentials;
    private final BackgroundJobMonitor jobs;

    @Scheduled(cron = "${app.account.token-cleanup-cron}")
    public void purgeExpiredTokens() {
        jobs.runScheduled("ACCOUNT_TOKEN_CLEANUP", credentials::purgeExpiredTokens);
    }
}
