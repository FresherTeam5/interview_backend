package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.repository.projection.InterviewSessionStatusCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"template", "profile"})
    Optional<InterviewSession> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM InterviewSession session WHERE session.id = :id")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM InterviewSession session
            WHERE session.id = :id
              AND session.user.id = :userId
            """)
    Optional<InterviewSession> findOwnedByIdForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId);

    List<InterviewSession> findByStatusAndCreatedAtBefore(
            InterviewSessionStatus status, Instant createdBefore);

    List<InterviewSession> findByStatusAndEndedAtBefore(
            InterviewSessionStatus status, Instant endedBefore);

    List<InterviewSession> findTop100ByStatusAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
            InterviewSessionStatus status, Instant deadline);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, InterviewSessionStatus status);

    long countByTemplateId(Long templateId);

    long countByUserIdAndCreatedAtBetween(Long userId, Instant createdFrom, Instant createdTo);

    long countByUserIdAndStatusAndCreatedAtBetween(
            Long userId,
            InterviewSessionStatus status,
            Instant createdFrom,
            Instant createdTo);

    @Query(value = """
            SELECT session
            FROM InterviewSession session
            WHERE session.user.id = :userId
              AND (:keyword IS NULL
                   OR LOWER(session.templateTitleSnapshot) LIKE :keyword
                   OR LOWER(session.profileNameSnapshot) LIKE :keyword)
              AND (:sessionStatus IS NULL OR session.status = :sessionStatus)
              AND (:sessionMode IS NULL OR session.mode = :sessionMode)
              AND (:createdFrom IS NULL OR session.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR session.createdAt <= :createdTo)
            """,
            countQuery = """
                    SELECT COUNT(session)
                    FROM InterviewSession session
                    WHERE session.user.id = :userId
                      AND (:keyword IS NULL
                           OR LOWER(session.templateTitleSnapshot) LIKE :keyword
                           OR LOWER(session.profileNameSnapshot) LIKE :keyword)
                      AND (:sessionStatus IS NULL OR session.status = :sessionStatus)
                      AND (:sessionMode IS NULL OR session.mode = :sessionMode)
                      AND (:createdFrom IS NULL OR session.createdAt >= :createdFrom)
                      AND (:createdTo IS NULL OR session.createdAt <= :createdTo)
                    """)
    Page<InterviewSession> searchForUser(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("sessionStatus") InterviewSessionStatus status,
            @Param("sessionMode") InterviewSessionMode mode,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    @Query("""
            SELECT session.status AS status, COUNT(session) AS total
            FROM InterviewSession session
            WHERE session.createdAt >= :createdAfter
            GROUP BY session.status
            """)
    List<InterviewSessionStatusCount> countStatusesCreatedAfter(
            @Param("createdAfter") Instant createdAfter);

    @Query(value = """
            SELECT session
            FROM InterviewSession session
            JOIN FETCH session.user user
            WHERE (:keyword IS NULL
                   OR LOWER(user.fullName) LIKE :keyword
                   OR LOWER(user.email) LIKE :keyword)
              AND (:sessionStatus IS NULL OR session.status = :sessionStatus)
              AND (:sessionMode IS NULL OR session.mode = :sessionMode)
              AND (:createdFrom IS NULL OR session.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR session.createdAt <= :createdTo)
            """,
            countQuery = """
                    SELECT COUNT(session)
                    FROM InterviewSession session
                    JOIN session.user user
                    WHERE (:keyword IS NULL
                           OR LOWER(user.fullName) LIKE :keyword
                           OR LOWER(user.email) LIKE :keyword)
                      AND (:sessionStatus IS NULL OR session.status = :sessionStatus)
                      AND (:sessionMode IS NULL OR session.mode = :sessionMode)
                      AND (:createdFrom IS NULL OR session.createdAt >= :createdFrom)
                      AND (:createdTo IS NULL OR session.createdAt <= :createdTo)
                    """)
    Page<InterviewSession> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("sessionStatus") InterviewSessionStatus status,
            @Param("sessionMode") InterviewSessionMode mode,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("SELECT session FROM InterviewSession session WHERE session.id = :id")
    Optional<InterviewSession> findByIdForAdmin(@Param("id") Long id);

    @Query(value = """
            SELECT session
            FROM InterviewSession session
            JOIN FETCH session.user user
            WHERE session.status = :sessionStatus
              AND session.updatedAt <= :staleBefore
            """,
            countQuery = """
                    SELECT COUNT(session)
                    FROM InterviewSession session
                    WHERE session.status = :sessionStatus
                      AND session.updatedAt <= :staleBefore
                    """)
    Page<InterviewSession> findStaleForAdmin(
            @Param("sessionStatus") InterviewSessionStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);
}
