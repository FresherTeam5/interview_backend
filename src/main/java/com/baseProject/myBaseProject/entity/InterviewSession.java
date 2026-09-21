package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "interview_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_interview_session_idempotency",
                columnNames = {"user_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_interview_session_user_status", columnList = "user_id, status"),
                @Index(name = "idx_interview_session_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_interview_session_deadline", columnList = "deadline_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private InterviewTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private CandidateProfile profile;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "template_title_snapshot", nullable = false, updatable = false, length = 200)
    private String templateTitleSnapshot;

    @Column(name = "profile_name_snapshot", nullable = false, updatable = false, length = 150)
    private String profileNameSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private InterviewSessionStatus status = InterviewSessionStatus.PREPARING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private InterviewSessionMode mode = InterviewSessionMode.TURN_BASED;

    @Column(name = "realtime_provider", length = 50)
    private String realtimeProvider;

    @Column(name = "realtime_voice_name", length = 50)
    private String realtimeVoiceName;

    @Column(name = "language_code", nullable = false, updatable = false, length = 10)
    private String languageCode;

    @Column(name = "duration_minutes", nullable = false, updatable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "interviewer_style", nullable = false, updatable = false, length = 20)
    private InterviewerStyle interviewerStyle;

    @Column(name = "template_snapshot_json", nullable = false, updatable = false, columnDefinition = "JSON")
    private String templateSnapshotJson;

    @Column(name = "profile_snapshot_json", nullable = false, updatable = false, columnDefinition = "JSON")
    private String profileSnapshotJson;

    @Column(name = "job_context_summary", columnDefinition = "TEXT")
    private String jobContextSummary;

    @Column(name = "candidate_context_summary", columnDefinition = "TEXT")
    private String candidateContextSummary;

    @Column(name = "opening_message", columnDefinition = "TEXT")
    private String openingMessage;

    @Column(name = "conversation_summary", columnDefinition = "MEDIUMTEXT")
    private String conversationSummary;

    @Column(name = "current_turn_index", nullable = false)
    @Builder.Default
    private int currentTurnIndex = 0;

    @Column(name = "preparation_error_code", length = 80)
    private String preparationErrorCode;

    @Column(name = "preparation_error_message", columnDefinition = "TEXT")
    private String preparationErrorMessage;

    @Column(name = "plan_schema_version", length = 20)
    private String planSchemaVersion;

    @Column(name = "plan_model_name", length = 100)
    private String planModelName;

    @Column(name = "plan_prompt_version", length = 20)
    private String planPromptVersion;

    @Column(name = "preparation_started_at")
    private Instant preparationStartedAt;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "end_reason", length = 30)
    private InterviewEndReason endReason;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "scoring_started_at")
    private Instant scoringStartedAt;

    @Column(name = "scoring_error_code", length = 80)
    private String scoringErrorCode;

    @Column(name = "scoring_error_message", columnDefinition = "TEXT")
    private String scoringErrorMessage;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void beginPreparation(Instant now) {
        preparationStartedAt = now;
        preparationErrorCode = null;
        preparationErrorMessage = null;
        updatedAt = now;
    }

    public void retryPreparation(Instant now) {
        status = InterviewSessionStatus.PREPARING;
        preparationStartedAt = null;
        preparedAt = null;
        preparationErrorCode = null;
        preparationErrorMessage = null;
        updatedAt = now;
    }

    public void markReady(String jobSummary, String candidateSummary, String opening,
                          String schemaVersion, String modelName, String promptVersion,
                          Instant now) {
        status = InterviewSessionStatus.READY;
        jobContextSummary = jobSummary;
        candidateContextSummary = candidateSummary;
        openingMessage = opening;
        planSchemaVersion = schemaVersion;
        planModelName = modelName;
        planPromptVersion = promptVersion;
        preparationErrorCode = null;
        preparationErrorMessage = null;
        preparedAt = now;
        updatedAt = now;
    }

    public void markPreparationFailed(String errorCode, String errorMessage, Instant now) {
        status = InterviewSessionStatus.PREPARATION_FAILED;
        preparationErrorCode = errorCode;
        preparationErrorMessage = errorMessage;
        preparedAt = null;
        updatedAt = now;
    }

    public void start(Instant now) {
        status = InterviewSessionStatus.IN_PROGRESS;
        startedAt = now;
        deadlineAt = now.plusSeconds(durationMinutes * 60L);
        lastActivityAt = now;
        endReason = null;
        endedAt = null;
        currentTurnIndex = 0;
        updatedAt = now;
    }

    public void recordTurn(int turnIndex, String summary, Instant now) {
        currentTurnIndex = turnIndex;
        if (summary != null) {
            conversationSummary = summary;
        }
        lastActivityAt = now;
        updatedAt = now;
    }

    public void fallbackToTurnBased(Instant now) {
        mode = InterviewSessionMode.TURN_BASED;
        updatedAt = now;
    }

    public void configureRealtime(String provider, String voiceName, Instant now) {
        realtimeProvider = provider;
        realtimeVoiceName = voiceName;
        updatedAt = now;
    }

    public void beginScoring(InterviewEndReason reason, Instant now) {
        status = InterviewSessionStatus.SCORING;
        endReason = reason;
        endedAt = now;
        scoringStartedAt = null;
        scoringErrorCode = null;
        scoringErrorMessage = null;
        completedAt = null;
        lastActivityAt = now;
        updatedAt = now;
    }

    public void markScoringStarted(Instant now) {
        scoringStartedAt = now;
        scoringErrorCode = null;
        scoringErrorMessage = null;
        updatedAt = now;
    }

    public void retryScoring(Instant now) {
        status = InterviewSessionStatus.SCORING;
        scoringStartedAt = null;
        scoringErrorCode = null;
        scoringErrorMessage = null;
        completedAt = null;
        updatedAt = now;
    }

    public void markScoringCompleted(Instant now) {
        status = InterviewSessionStatus.COMPLETED;
        scoringErrorCode = null;
        scoringErrorMessage = null;
        completedAt = now;
        updatedAt = now;
    }

    public void markScoringFailed(String errorCode, String errorMessage, Instant now) {
        status = InterviewSessionStatus.SCORING_FAILED;
        scoringErrorCode = errorCode;
        scoringErrorMessage = errorMessage;
        completedAt = null;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        status = InterviewSessionStatus.CANCELLED;
        endReason = InterviewEndReason.USER_CANCELLED;
        endedAt = now;
        lastActivityAt = now;
        updatedAt = now;
    }

    public void terminateByAdmin(Instant now) {
        status = InterviewSessionStatus.CANCELLED;
        endReason = InterviewEndReason.SYSTEM_TERMINATED;
        endedAt = now;
        lastActivityAt = now;
        updatedAt = now;
    }
}
