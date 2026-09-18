package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageDeletionJob {
    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final Clock clock;

    @Scheduled(cron = "${app.account.storage-deletion-cron}")
    public void deleteQueuedObjects() {
        Instant now = clock.instant();
        List<Task> tasks = jdbc.query("""
                SELECT id, storage_key, attempts
                FROM storage_deletion_tasks
                WHERE next_attempt_at <= ?
                ORDER BY next_attempt_at, id
                LIMIT 100
                """, (result, row) -> new Task(result.getLong("id"),
                        result.getString("storage_key"), result.getInt("attempts")), now);
        tasks.forEach(this::delete);
    }

    private void delete(Task task) {
        try {
            storage.delete(task.storageKey());
            jdbc.update("DELETE FROM storage_deletion_tasks WHERE id = ?", task.id());
        } catch (RuntimeException exception) {
            int attempts = task.attempts() + 1;
            long delayMinutes = Math.min(60, 1L << Math.min(attempts, 6));
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            jdbc.update("""
                    UPDATE storage_deletion_tasks
                    SET attempts = ?, last_error = ?, next_attempt_at = ?
                    WHERE id = ?
                    """, attempts, message,
                    clock.instant().plus(delayMinutes, ChronoUnit.MINUTES), task.id());
            log.warn("Cannot delete queued storage object id={}, attempt={}",
                    task.id(), attempts, exception);
        }
    }

    private record Task(long id, String storageKey, int attempts) {
    }
}
