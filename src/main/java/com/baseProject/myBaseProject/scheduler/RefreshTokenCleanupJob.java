package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.service.RefreshTokenService;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {
    private final RefreshTokenService refreshTokenService;
    private final BackgroundJobMonitor jobs;

    @Scheduled(cron = "${app.refresh-token.cleanup-cron}")
    public void purgeExpiredTokens() {
        int deleted = jobs.runScheduled("REFRESH_TOKEN_CLEANUP",
                refreshTokenService::purgeExpired);
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }
}
