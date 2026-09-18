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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "interview_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_source_jd", columnNames = "source_job_description_id"),
        indexes = {
                @Index(name = "idx_template_owner", columnList = "owner_id, created_at"),
                @Index(name = "idx_template_public", columnList = "published_at, archived_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class InterviewTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_job_description_id", updatable = false)
    private JobDescriptionDocument sourceJobDescription;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "target_seniority", length = 100)
    private String targetSeniority;

    @Column(name = "content_json", nullable = false, columnDefinition = "JSON")
    private String contentJson;

    @Column(name = "content_schema_version", nullable = false, length = 20)
    private String contentSchemaVersion;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
