package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.SupportTicketEventType;
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
@Table(name = "support_ticket_events", indexes = @Index(
        name = "idx_support_event_ticket_created", columnList = "ticket_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private UserAccount actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private SupportTicketEventType eventType;

    @Column(name = "from_value", updatable = false, length = 100)
    private String fromValue;

    @Column(name = "to_value", updatable = false, length = 100)
    private String toValue;

    @Column(updatable = false, length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
