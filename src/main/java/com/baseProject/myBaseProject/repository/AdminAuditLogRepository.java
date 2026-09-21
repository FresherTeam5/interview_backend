package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.AdminAuditLog;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query(value = """
            SELECT log
            FROM AdminAuditLog log
            LEFT JOIN FETCH log.actor actor
            WHERE (:actorId IS NULL OR actor.id = :actorId)
              AND (:action IS NULL OR log.action = :action)
              AND (:resourceType IS NULL OR log.resourceType = :resourceType)
              AND (:resourceId IS NULL OR log.resourceId = :resourceId)
              AND (:createdFrom IS NULL OR log.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR log.createdAt <= :createdTo)
            """,
            countQuery = """
                    SELECT COUNT(log)
                    FROM AdminAuditLog log
                    LEFT JOIN log.actor actor
                    WHERE (:actorId IS NULL OR actor.id = :actorId)
                      AND (:action IS NULL OR log.action = :action)
                      AND (:resourceType IS NULL OR log.resourceType = :resourceType)
                      AND (:resourceId IS NULL OR log.resourceId = :resourceId)
                      AND (:createdFrom IS NULL OR log.createdAt >= :createdFrom)
                      AND (:createdTo IS NULL OR log.createdAt <= :createdTo)
                    """)
    Page<AdminAuditLog> search(
            @Param("actorId") Long actorId,
            @Param("action") AdminAuditAction action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);
}
