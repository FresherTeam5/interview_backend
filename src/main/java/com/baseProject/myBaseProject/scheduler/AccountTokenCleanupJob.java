package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.service.AccountCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountTokenCleanupJob {
    private final AccountCredentialService credentials;

    @Scheduled(cron = "${app.account.token-cleanup-cron}")
    public void purgeExpiredTokens() {
        credentials.purgeExpiredTokens();
    }
}
