package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SupportTicket;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    @EntityGraph(attributePaths = {"session", "turn"})
    Page<SupportTicket> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"session", "turn"})
    Optional<SupportTicket> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ticket FROM SupportTicket ticket WHERE ticket.id = :id")
    Optional<SupportTicket> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "session", "turn", "assignedAdmin"})
    @Query(value = """
            SELECT ticket
            FROM SupportTicket ticket
            JOIN ticket.user user
            LEFT JOIN ticket.assignedAdmin assigned
            WHERE (:keyword IS NULL
                   OR LOWER(ticket.referenceCode) LIKE :keyword
                   OR LOWER(ticket.subject) LIKE :keyword
                   OR LOWER(user.fullName) LIKE :keyword
                   OR LOWER(user.email) LIKE :keyword)
              AND (:status IS NULL OR ticket.status = :status)
              AND (:ticketType IS NULL OR ticket.type = :ticketType)
              AND (:priority IS NULL OR ticket.priority = :priority)
              AND (:assignedAdminId IS NULL OR assigned.id = :assignedAdminId)
              AND (:createdFrom IS NULL OR ticket.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR ticket.createdAt <= :createdTo)
            """,
            countQuery = """
                    SELECT COUNT(ticket)
                    FROM SupportTicket ticket
                    JOIN ticket.user user
                    LEFT JOIN ticket.assignedAdmin assigned
                    WHERE (:keyword IS NULL
                           OR LOWER(ticket.referenceCode) LIKE :keyword
                           OR LOWER(ticket.subject) LIKE :keyword
                           OR LOWER(user.fullName) LIKE :keyword
                           OR LOWER(user.email) LIKE :keyword)
                      AND (:status IS NULL OR ticket.status = :status)
                      AND (:ticketType IS NULL OR ticket.type = :ticketType)
                      AND (:priority IS NULL OR ticket.priority = :priority)
                      AND (:assignedAdminId IS NULL OR assigned.id = :assignedAdminId)
                      AND (:createdFrom IS NULL OR ticket.createdAt >= :createdFrom)
                      AND (:createdTo IS NULL OR ticket.createdAt <= :createdTo)
                    """)
    Page<SupportTicket> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("status") SupportTicketStatus status,
            @Param("ticketType") SupportTicketType type,
            @Param("priority") SupportTicketPriority priority,
            @Param("assignedAdminId") Long assignedAdminId,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);
}
