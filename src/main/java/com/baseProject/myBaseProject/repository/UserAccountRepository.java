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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM UserAccount user WHERE user.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}
