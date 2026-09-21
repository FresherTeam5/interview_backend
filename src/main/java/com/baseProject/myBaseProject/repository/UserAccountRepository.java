package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    boolean existsByEmail(String email);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByGoogleId(String googleId);

    boolean existsByRole(UserRole role);

    long countByEnabledTrue();

    long countByCreatedAtGreaterThanEqual(Instant createdAfter);

    @Query(value = """
            SELECT user
            FROM UserAccount user
            WHERE (:keyword IS NULL
                   OR LOWER(user.fullName) LIKE :keyword
                   OR LOWER(user.email) LIKE :keyword)
              AND (:role IS NULL OR user.role = :role)
              AND (:enabled IS NULL OR user.enabled = :enabled)
            """,
            countQuery = """
                    SELECT COUNT(user)
                    FROM UserAccount user
                    WHERE (:keyword IS NULL
                           OR LOWER(user.fullName) LIKE :keyword
                           OR LOWER(user.email) LIKE :keyword)
                      AND (:role IS NULL OR user.role = :role)
                      AND (:enabled IS NULL OR user.enabled = :enabled)
                    """)
    Page<UserAccount> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("role") UserRole role,
            @Param("enabled") Boolean enabled,
            Pageable pageable);

    @Query(value = """
            SELECT user
            FROM UserAccount user
            LEFT JOIN AccountDeletionRequest deletion ON deletion.user = user
            WHERE (:keyword IS NULL
                   OR LOWER(user.fullName) LIKE :keyword
                   OR LOWER(user.email) LIKE :keyword)
              AND user.role = com.baseProject.myBaseProject.enums.UserRole.USER
              AND (:emailVerified IS NULL
                   OR (:emailVerified = TRUE AND user.emailVerifiedAt IS NOT NULL)
                   OR (:emailVerified = FALSE AND user.emailVerifiedAt IS NULL))
              AND (:accessStatus IS NULL
                   OR (:accessStatus = 'DISABLED'
                       AND user.enabled = FALSE)
                   OR (:accessStatus = 'SUSPENDED'
                       AND user.enabled = TRUE AND user.suspendedAt IS NOT NULL
                       AND (user.suspendedUntil IS NULL OR user.suspendedUntil > :now))
                   OR (:accessStatus = 'ACTIVE'
                       AND user.enabled = TRUE
                       AND (user.suspendedAt IS NULL
                            OR (user.suspendedUntil IS NOT NULL AND user.suspendedUntil <= :now))))
              AND (:deletionStatus IS NULL OR deletion.status = :deletionStatus)
              AND (:lastLoginFrom IS NULL OR user.lastLoginAt >= :lastLoginFrom)
              AND (:lastLoginTo IS NULL OR user.lastLoginAt <= :lastLoginTo)
            """,
            countQuery = """
                    SELECT COUNT(user)
                    FROM UserAccount user
                    LEFT JOIN AccountDeletionRequest deletion ON deletion.user = user
                    WHERE (:keyword IS NULL
                           OR LOWER(user.fullName) LIKE :keyword
                           OR LOWER(user.email) LIKE :keyword)
                      AND user.role = com.baseProject.myBaseProject.enums.UserRole.USER
                      AND (:emailVerified IS NULL
                           OR (:emailVerified = TRUE AND user.emailVerifiedAt IS NOT NULL)
                           OR (:emailVerified = FALSE AND user.emailVerifiedAt IS NULL))
                      AND (:accessStatus IS NULL
                           OR (:accessStatus = 'DISABLED'
                               AND user.enabled = FALSE)
                           OR (:accessStatus = 'SUSPENDED'
                               AND user.enabled = TRUE AND user.suspendedAt IS NOT NULL
                               AND (user.suspendedUntil IS NULL OR user.suspendedUntil > :now))
                           OR (:accessStatus = 'ACTIVE'
                               AND user.enabled = TRUE
                               AND (user.suspendedAt IS NULL
                                    OR (user.suspendedUntil IS NOT NULL AND user.suspendedUntil <= :now))))
                      AND (:deletionStatus IS NULL OR deletion.status = :deletionStatus)
                      AND (:lastLoginFrom IS NULL OR user.lastLoginAt >= :lastLoginFrom)
                      AND (:lastLoginTo IS NULL OR user.lastLoginAt <= :lastLoginTo)
                    """)
    Page<UserAccount> searchAdvancedForAdmin(
            @Param("keyword") String keyword,
            @Param("emailVerified") Boolean emailVerified,
            @Param("accessStatus") String accessStatus,
            @Param("deletionStatus") AccountDeletionStatus deletionStatus,
            @Param("lastLoginFrom") Instant lastLoginFrom,
            @Param("lastLoginTo") Instant lastLoginTo,
            @Param("now") Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM UserAccount user WHERE user.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}
