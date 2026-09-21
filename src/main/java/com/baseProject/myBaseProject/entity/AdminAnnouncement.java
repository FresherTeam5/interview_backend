package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "admin_announcements", indexes = @Index(
        name = "idx_announcement_status_schedule", columnList = "status, scheduled_at"))
@Getter
@Setter
@NoArgsConstructor
public class AdminAnnouncement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private UserAccount createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnnouncementAudience audience;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_recipients", nullable = false)
    private long totalRecipients;

    @Column(name = "delivered_count", nullable = false)
    private long deliveredCount;

    @Column(name = "failed_count", nullable = false)
    private long failedCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
