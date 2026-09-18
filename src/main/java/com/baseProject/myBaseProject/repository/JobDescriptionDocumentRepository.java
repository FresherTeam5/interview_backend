package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobDescriptionDocumentRepository extends JpaRepository<JobDescriptionDocument, Long> {
    List<JobDescriptionDocument> findByOwnerIdAndActiveTrueOrderByUploadedAtDesc(Long ownerId);

    Optional<JobDescriptionDocument> findByIdAndOwnerIdAndActiveTrue(Long id, Long ownerId);

    Optional<JobDescriptionDocument> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerIdAndActiveTrue(Long ownerId);

    List<JobDescriptionDocument> findByStatusInAndUploadedAtBefore(
            Collection<JobDescriptionStatus> statuses, Instant uploadedBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM JobDescriptionDocument d WHERE d.id = :id")
    Optional<JobDescriptionDocument> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT document.storageKey
            FROM JobDescriptionDocument document
            WHERE document.owner.id = :userId
              AND document.storageKey IS NOT NULL
            """)
    List<String> findStorageKeysByOwnerId(@Param("userId") Long userId);
}
