package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.service.AdminAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional(readOnly = true)
class AdminRepositoryQueryTest {
    @Autowired
    private UserAccountRepository users;

    @Autowired
    private InterviewSessionRepository sessions;

    @Autowired
    private SupportTicketRepository supportTickets;

    @Autowired
    private InterviewFeedbackRepository feedback;

    @Autowired
    private InterviewTemplateRepository templates;

    @Autowired
    private AdminAuditLogRepository auditLogs;

    @Autowired
    private BackgroundJobRunRepository jobRuns;

    @Autowired
    private AdminAnalyticsService analytics;

    @Test
    void optionalAdminFiltersExecuteWithNullValues() {
        assertThatCode(() -> users.searchForAdmin(
                null, null, null, PageRequest.of(0, 1)).getTotalElements())
                .doesNotThrowAnyException();

        assertThatCode(() -> sessions.searchForAdmin(
                null, null, null, null, null, PageRequest.of(0, 1))
                .getTotalElements())
                .doesNotThrowAnyException();

        assertThatCode(() -> sessions.countStatusesCreatedAfter(Instant.EPOCH))
                .doesNotThrowAnyException();
    }

    @Test
    void adminPlatformQueriesAndAnalyticsExecuteAgainstMySql() {
        PageRequest first = PageRequest.of(0, 1);
        Instant now = Instant.now();

        assertThatCode(() -> users.searchAdvancedForAdmin(
                null, null, null, null, null, null, now, first).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> supportTickets.searchForAdmin(
                null, null, null, null, null, null, null, first).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> feedback.searchForAdmin(
                null, null, null, null, first).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> templates.searchForAdmin(
                null, null, null, null, null, null, first).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> auditLogs.search(
                null, null, null, null, null, null, first).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> jobRuns.search(
                null, null, null, null, first).getTotalElements())
                .doesNotThrowAnyException();
        assertThatCode(() -> analytics.summary(now.minusSeconds(86_400), now))
                .doesNotThrowAnyException();
        assertThatCode(() -> analytics.timeSeries(now.minusSeconds(86_400), now))
                .doesNotThrowAnyException();
    }
}
