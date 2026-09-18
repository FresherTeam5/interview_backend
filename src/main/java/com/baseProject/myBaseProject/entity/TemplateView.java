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

import java.time.Instant;

@Entity
@Table(name = "user_template_views",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_template_view",
                columnNames = {"user_id", "template_id"}),
        indexes = @Index(name = "idx_template_views_recent", columnList = "user_id, last_viewed_at"))
@Getter
@Setter
@NoArgsConstructor
public class TemplateView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private InterviewTemplate template;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "last_viewed_at", nullable = false)
    private Instant lastViewedAt;
}
