package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.BackgroundJobStatus;
import com.baseProject.myBaseProject.enums.BackgroundJobTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "background_job_runs", indexes = {
        @Index(name = "idx_job_run_name_started", columnList = "job_name, started_at"),
        @Index(name = "idx_job_run_status_started", columnList = "status, started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class BackgroundJobRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, updatable = false, length = 80)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false, length = 20)
    private BackgroundJobTrigger trigger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by", updatable = false)
    private UserAccount triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackgroundJobStatus status;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
