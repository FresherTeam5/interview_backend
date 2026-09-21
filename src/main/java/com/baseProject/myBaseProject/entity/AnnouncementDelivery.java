package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.AnnouncementDeliveryStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "announcement_deliveries",
        uniqueConstraints = @UniqueConstraint(name = "uq_announcement_delivery_user",
                columnNames = {"announcement_id", "user_id"}),
        indexes = @Index(name = "idx_announcement_delivery_status",
                columnList = "announcement_id, status, id"))
@Getter
@Setter
@NoArgsConstructor
public class AnnouncementDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "announcement_id", nullable = false, updatable = false)
    private AdminAnnouncement announcement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
