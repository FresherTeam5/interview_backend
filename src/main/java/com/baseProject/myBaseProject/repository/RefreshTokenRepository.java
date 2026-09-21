package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(
            Long userId, Instant now);

    boolean existsByUserIdAndFamilyId(Long userId, String familyId);

    long countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.familyId = :familyId
               AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.user.id = :userId
               AND t.familyId = :familyId
               AND t.revokedAt IS NULL
            """)
    int revokeFamilyForUser(
            @Param("userId") Long userId,
            @Param("familyId") String familyId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.user.id = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
