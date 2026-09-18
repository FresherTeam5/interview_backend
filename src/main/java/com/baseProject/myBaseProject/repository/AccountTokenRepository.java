package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.AccountToken;
import com.baseProject.myBaseProject.enums.AccountTokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM AccountToken token WHERE token.tokenHash = :tokenHash")
    Optional<AccountToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE AccountToken token
            SET token.usedAt = :usedAt
            WHERE token.user.id = :userId
              AND token.tokenType = :tokenType
              AND token.usedAt IS NULL
            """)
    int invalidateUnused(
            @Param("userId") Long userId,
            @Param("tokenType") AccountTokenType tokenType,
            @Param("usedAt") Instant usedAt);

    @Modifying
    @Query("DELETE FROM AccountToken token WHERE token.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
