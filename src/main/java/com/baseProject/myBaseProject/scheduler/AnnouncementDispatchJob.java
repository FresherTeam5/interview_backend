package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.service.AnnouncementDispatchService;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementDispatchJob {
    private final AnnouncementDispatchService dispatch;
    private final BackgroundJobMonitor jobs;

    @Scheduled(cron = "${app.admin.announcement-cron:0 * * * * *}")
    public void dispatch() {
        int processed = jobs.runScheduled("ANNOUNCEMENT_DISPATCH", dispatch::dispatchDue);
        if (processed > 0) {
            log.info("Processed {} announcement delivery or deliveries", processed);
        }
    }
}
