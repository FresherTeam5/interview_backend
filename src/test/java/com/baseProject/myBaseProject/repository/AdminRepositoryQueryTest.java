package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.service.AdminAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private JdbcTemplate jdbc;

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

    @Test
    void transitionActorConstraintsAllowAdmin() {
        List<String> actorChecks = jdbc.queryForList("""
                SELECT cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON cc.constraint_schema = tc.constraint_schema
                 AND cc.constraint_name = tc.constraint_name
                WHERE tc.constraint_schema = DATABASE()
                  AND tc.table_name = 'interview_session_transitions'
                  AND LOWER(cc.check_clause) LIKE '%actor%'
                """, String.class);

        assertThat(actorChecks)
                .isNotEmpty()
                .allMatch(clause -> clause.toUpperCase().contains("ADMIN"));
    }
}
