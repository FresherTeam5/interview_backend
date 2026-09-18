package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountDeletionRequestRepository
        extends JpaRepository<AccountDeletionRequest, Long> {
    Optional<AccountDeletionRequest> findByUserId(Long userId);

    List<AccountDeletionRequest> findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            com.baseProject.myBaseProject.enums.AccountDeletionStatus status, Instant scheduledAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM AccountDeletionRequest request WHERE request.id = :id")
    Optional<AccountDeletionRequest> findByIdForUpdate(@Param("id") Long id);
}
