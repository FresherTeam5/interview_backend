package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.interview.InterviewDeadlineService;
import com.baseProject.myBaseProject.service.BackgroundJobMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewDeadlineJob {
    private final InterviewDeadlineService deadlineService;
    private final BackgroundJobMonitor jobs;

    @Scheduled(fixedDelayString = "${app.interview-session.deadline-sweep-ms:30000}")
    public void closeExpiredSessions() {
        int closed = jobs.runScheduled("INTERVIEW_DEADLINE",
                deadlineService::closeExpiredSessions);
        if (closed > 0) {
            log.info("Closed {} interview sessions after their deadline", closed);
        }
    }
}
