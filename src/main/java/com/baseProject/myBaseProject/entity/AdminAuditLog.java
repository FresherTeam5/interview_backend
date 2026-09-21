package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.AdminAuditAction;
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
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Table(name = "admin_audit_logs", indexes = {
        @Index(name = "idx_admin_audit_actor_created", columnList = "actor_id, created_at"),
        @Index(name = "idx_admin_audit_resource_created", columnList = "resource_type, resource_id, created_at"),
        @Index(name = "idx_admin_audit_action_created", columnList = "action, created_at")
})
@Getter
@NoArgsConstructor
@Immutable
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private UserAccount actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 80)
    private AdminAuditAction action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 60)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 100)
    private String resourceId;

    @Column(name = "before_json", updatable = false, columnDefinition = "JSON")
    private String beforeJson;

    @Column(name = "after_json", updatable = false, columnDefinition = "JSON")
    private String afterJson;

    @Column(name = "request_id", nullable = false, updatable = false, length = 100)
    private String requestId;

    @Column(name = "ip_address", updatable = false, length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AdminAuditLog(
            UserAccount actor,
            AdminAuditAction action,
            String resourceType,
            String resourceId,
            String beforeJson,
            String afterJson,
            String requestId,
            String ipAddress,
            Instant createdAt) {
        this.actor = actor;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.requestId = requestId;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
    }
}
