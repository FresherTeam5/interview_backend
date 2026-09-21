package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.AdminAnnouncement;
import com.baseProject.myBaseProject.entity.AnnouncementDelivery;
import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementDeliveryStatus;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;
import com.baseProject.myBaseProject.repository.AdminAnnouncementRepository;
import com.baseProject.myBaseProject.repository.AnnouncementDeliveryRepository;
import com.baseProject.myBaseProject.service.AnnouncementDispatchService;
import com.baseProject.myBaseProject.service.SystemSettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AnnouncementDispatchServiceImpl implements AnnouncementDispatchService {
    private static final int MAX_ATTEMPTS = 3;

    private final AdminAnnouncementRepository announcements;
    private final AnnouncementDeliveryRepository deliveries;
    private final AnnouncementDeliveryProcessor processor;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SystemSettingService systemSettings;

    public AnnouncementDispatchServiceImpl(
            AdminAnnouncementRepository announcements,
            AnnouncementDeliveryRepository deliveries,
            AnnouncementDeliveryProcessor processor,
            JdbcTemplate jdbc,
            Clock clock,
            PlatformTransactionManager transactionManager,
            SystemSettingService systemSettings) {
        this.announcements = announcements;
        this.deliveries = deliveries;
        this.processor = processor;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.systemSettings = systemSettings;
    }

    @Override
    public int dispatchDue() {
        if (!systemSettings.booleanValue(
                SystemSettingServiceImpl.ANNOUNCEMENTS_ENABLED, true)) {
            return 0;
        }
        List<Long> ids = transactions.execute(status -> prepare());
        if (ids == null) {
            return 0;
        }
        int processed = 0;
        for (Long announcementId : ids) {
            processed += processPending(announcementId);
            transactions.executeWithoutResult(status -> refreshProgress(announcementId));
        }
        return processed;
    }

    private List<Long> prepare() {
        Instant now = clock.instant();
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        announcements.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        AnnouncementStatus.SCHEDULED, now)
                .forEach(value -> candidateIds.add(value.getId()));
        announcements.findTop20ByStatusInOrderByStartedAtAsc(
                        List.of(AnnouncementStatus.PROCESSING))
                .forEach(value -> candidateIds.add(value.getId()));

        List<Long> active = new ArrayList<>();
        for (Long id : candidateIds) {
            AdminAnnouncement value = announcements.findByIdForUpdate(id).orElse(null);
            if (value == null) {
                continue;
            }
            if (value.getStatus() == AnnouncementStatus.SCHEDULED
                    && !value.getScheduledAt().isAfter(now)) {
                value.setStatus(AnnouncementStatus.PROCESSING);
                value.setStartedAt(now);
                value.setUpdatedAt(now);
            }
            if (value.getStatus() != AnnouncementStatus.PROCESSING) {
                continue;
            }
            insertRecipients(value, now);
            value.setTotalRecipients(deliveries.countByAnnouncementId(id));
            active.add(id);
        }
        return active;
    }

    private int processPending(Long announcementId) {
        int processed = processAll(announcementId, AnnouncementDeliveryStatus.PENDING);
        if (processed == 0) {
            List<AnnouncementDelivery> retry = deliveries
                    .findTop100ByAnnouncementIdAndStatusInAndAttemptsLessThanOrderById(
                            announcementId, List.of(AnnouncementDeliveryStatus.FAILED), MAX_ATTEMPTS);
            retry.forEach(delivery -> processor.process(delivery.getId()));
            processed = retry.size();
        }
        return processed;
    }

    private int processAll(Long announcementId, AnnouncementDeliveryStatus status) {
        int processed = 0;
        List<AnnouncementDelivery> batch;
        do {
            batch = deliveries.findTop100ByAnnouncementIdAndStatusInAndAttemptsLessThanOrderById(
                    announcementId, List.of(status), MAX_ATTEMPTS);
            batch.forEach(delivery -> processor.process(delivery.getId()));
            processed += batch.size();
        } while (batch.size() == 100);
        return processed;
    }

    private void refreshProgress(Long announcementId) {
        AdminAnnouncement value = announcements.findByIdForUpdate(announcementId).orElse(null);
        if (value == null || value.getStatus() != AnnouncementStatus.PROCESSING) {
            return;
        }
        long delivered = deliveries.countByAnnouncementIdAndStatus(
                announcementId, AnnouncementDeliveryStatus.DELIVERED);
        long failed = deliveries.countByAnnouncementIdAndStatus(
                announcementId, AnnouncementDeliveryStatus.FAILED);
        value.setDeliveredCount(delivered);
        value.setFailedCount(failed);
        value.setUpdatedAt(clock.instant());
        long retryable = deliveries.countByAnnouncementIdAndStatusInAndAttemptsLessThan(
                announcementId,
                EnumSet.of(AnnouncementDeliveryStatus.PENDING, AnnouncementDeliveryStatus.FAILED),
                MAX_ATTEMPTS);
        if (retryable == 0) {
            value.setStatus(failed == 0
                    ? AnnouncementStatus.SENT : AnnouncementStatus.PARTIALLY_FAILED);
            value.setCompletedAt(clock.instant());
            value.setLastError(failed == 0 ? null : failed + " delivery or deliveries failed");
        }
    }

    private void insertRecipients(AdminAnnouncement value, Instant now) {
        String audienceCondition = switch (value.getAudience()) {
            case ALL_USERS -> "";
            case VERIFIED_USERS -> " AND email_verified_at IS NOT NULL";
            case ACTIVE_USERS -> " AND last_login_at >= ?";
        };
        String sql = """
                INSERT IGNORE INTO announcement_deliveries
                    (announcement_id, user_id, status, attempts, created_at, updated_at)
                SELECT ?, id, 'PENDING', 0, ?, ?
                FROM user_accounts
                WHERE role = 'USER'
                  AND enabled = TRUE
                  AND deletion_requested_at IS NULL
                  AND (suspended_at IS NULL
                       OR (suspended_until IS NOT NULL AND suspended_until <= ?))
                """ + audienceCondition;
        if (value.getAudience() == AnnouncementAudience.ACTIVE_USERS) {
            jdbc.update(sql, value.getId(), now, now, now,
                    now.minus(30, ChronoUnit.DAYS));
        } else {
            jdbc.update(sql, value.getId(), now, now, now);
        }
    }
}
