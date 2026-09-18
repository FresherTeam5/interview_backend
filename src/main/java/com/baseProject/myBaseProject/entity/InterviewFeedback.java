package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "interview_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uq_interview_feedback_session_user",
                columnNames = {"session_id", "user_id"}),
        indexes = @Index(name = "idx_interview_feedback_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class InterviewFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(name = "question_rating")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer questionRating;

    @Column(name = "voice_rating")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer voiceRating;

    @Column(name = "report_rating")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer reportRating;

    @Column(length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
