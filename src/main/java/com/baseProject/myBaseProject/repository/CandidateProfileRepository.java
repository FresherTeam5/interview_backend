package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    @Query("""
            SELECT profile
            FROM CandidateProfile profile
            LEFT JOIN FETCH profile.cvDocument document
            WHERE profile.user.id = :userId
              AND (document IS NULL OR document.active = true)
            ORDER BY profile.createdAt DESC
            """)
    List<CandidateProfile> findAvailableByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT profile
            FROM CandidateProfile profile
            LEFT JOIN FETCH profile.cvDocument document
            WHERE profile.id = :id
              AND profile.user.id = :userId
              AND (document IS NULL OR document.active = true)
            """)
    Optional<CandidateProfile> findAvailableByIdAndUserId(
            @Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT profile
            FROM CandidateProfile profile
            LEFT JOIN FETCH profile.cvDocument document
            WHERE profile.id = :profileId
              AND profile.user.id = :userId
              AND (document IS NULL OR document.active = true)
            """)
    Optional<CandidateProfile> findActiveOwnedByIdForUpdate(
            @Param("profileId") Long profileId,
            @Param("userId") Long userId);

    Optional<CandidateProfile> findByCvDocumentId(Long cvDocumentId);

    List<CandidateProfile> findByCvDocumentIdIn(Collection<Long> cvDocumentIds);

    boolean existsByCvDocumentId(Long cvDocumentId);
}
