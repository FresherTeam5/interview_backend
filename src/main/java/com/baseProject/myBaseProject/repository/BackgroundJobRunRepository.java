package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.BackgroundJobRun;
import com.baseProject.myBaseProject.enums.BackgroundJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;

public interface BackgroundJobRunRepository extends JpaRepository<BackgroundJobRun, Long> {
    @Query(value = """
            SELECT run
            FROM BackgroundJobRun run
            LEFT JOIN FETCH run.triggeredBy admin
            WHERE (:jobName IS NULL OR run.jobName = :jobName)
              AND (:jobStatus IS NULL OR run.status = :jobStatus)
              AND (:startedFrom IS NULL OR run.startedAt >= :startedFrom)
              AND (:startedTo IS NULL OR run.startedAt <= :startedTo)
            """,
            countQuery = """
                    SELECT COUNT(run)
                    FROM BackgroundJobRun run
                    WHERE (:jobName IS NULL OR run.jobName = :jobName)
                      AND (:jobStatus IS NULL OR run.status = :jobStatus)
                      AND (:startedFrom IS NULL OR run.startedAt >= :startedFrom)
                      AND (:startedTo IS NULL OR run.startedAt <= :startedTo)
                    """)
    Page<BackgroundJobRun> search(
            @Param("jobName") String jobName,
            @Param("jobStatus") BackgroundJobStatus status,
            @Param("startedFrom") Instant from,
            @Param("startedTo") Instant to,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM BackgroundJobRun run WHERE run.finishedAt < :cutoff")
    int deleteCompletedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("""
            UPDATE BackgroundJobRun run
            SET run.status = com.baseProject.myBaseProject.enums.BackgroundJobStatus.FAILED,
                run.errorMessage = :message,
                run.finishedAt = :finishedAt
            WHERE run.status = com.baseProject.myBaseProject.enums.BackgroundJobStatus.RUNNING
              AND run.startedAt < :startedBefore
            """)
    int failInterruptedRuns(
            @Param("startedBefore") Instant startedBefore,
            @Param("finishedAt") Instant finishedAt,
            @Param("message") String message);
}
