package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.SupportMessageVisibility;
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
@Table(name = "support_ticket_messages", indexes = @Index(
        name = "idx_support_message_ticket_created", columnList = "ticket_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private SupportTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", updatable = false)
    private UserAccount sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private SupportMessageVisibility visibility;

    @Column(nullable = false, updatable = false, length = 5000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
