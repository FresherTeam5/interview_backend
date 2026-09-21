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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AccountDeletionRequestRepository
        extends JpaRepository<AccountDeletionRequest, Long> {
    Optional<AccountDeletionRequest> findByUserId(Long userId);

    List<AccountDeletionRequest> findTop50ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            com.baseProject.myBaseProject.enums.AccountDeletionStatus status, Instant scheduledAt);

    List<AccountDeletionRequest> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            com.baseProject.myBaseProject.enums.AccountDeletionStatus status, Instant nextAttemptAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM AccountDeletionRequest request WHERE request.id = :id")
    Optional<AccountDeletionRequest> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "user")
    Page<AccountDeletionRequest> findByStatus(
            com.baseProject.myBaseProject.enums.AccountDeletionStatus status,
            Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<AccountDeletionRequest> findAll(Pageable pageable);
}
