package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewTemplateRepository extends JpaRepository<InterviewTemplate, Long> {
    Optional<InterviewTemplate> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<InterviewTemplate> findBySourceJobDescriptionId(Long jobDescriptionId);

    @Query("""
            SELECT t FROM InterviewTemplate t JOIN FETCH t.sourceJobDescription d
            WHERE t.owner.id = :ownerId
                AND d.checksumSha256 = :checksum
                AND d.status = :status
                AND t.confirmedAt IS NULL
                AND t.archivedAt IS NULL
            ORDER BY d.uploadedAt DESC
            """)
    List<InterviewTemplate> findReusable(
            @Param("ownerId") Long ownerId,
            @Param("checksum") String checksum,
            @Param("status") JobDescriptionStatus status,
            Pageable pageable);

    List<InterviewTemplate> findBySourceJobDescriptionIdIn(Collection<Long> jobDescriptionIds);

    Page<InterviewTemplate> findByOwnerId(Long ownerId, Pageable pageable);

    Page<InterviewTemplate> findByPublishedAtIsNotNullAndArchivedAtIsNull(Pageable pageable);

    @Query("""
            SELECT template
            FROM InterviewTemplate template
            WHERE template.owner.id = :userId
              AND (:keyword IS NULL
                   OR LOWER(template.title) LIKE :keyword
                   OR LOWER(template.jobTitle) LIKE :keyword
                   OR LOWER(template.contentJson) LIKE :keyword)
              AND (:seniority IS NULL OR LOWER(template.targetSeniority) = :seniority)
              AND (:language IS NULL OR LOWER(template.contentJson) LIKE :language)
              AND (:technology IS NULL OR LOWER(template.contentJson) LIKE :technology)
            """)
    Page<InterviewTemplate> searchMine(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("seniority") String seniority,
            @Param("language") String language,
            @Param("technology") String technology,
            Pageable pageable);

    @Query("""
            SELECT template
            FROM InterviewTemplate template
            WHERE template.publishedAt IS NOT NULL
              AND template.archivedAt IS NULL
              AND (:keyword IS NULL
                   OR LOWER(template.title) LIKE :keyword
                   OR LOWER(template.jobTitle) LIKE :keyword
                   OR LOWER(template.contentJson) LIKE :keyword)
              AND (:seniority IS NULL OR LOWER(template.targetSeniority) = :seniority)
              AND (:language IS NULL OR LOWER(template.contentJson) LIKE :language)
              AND (:technology IS NULL OR LOWER(template.contentJson) LIKE :technology)
            """)
    Page<InterviewTemplate> searchPublic(
            @Param("keyword") String keyword,
            @Param("seniority") String seniority,
            @Param("language") String language,
            @Param("technology") String technology,
            Pageable pageable);

    Optional<InterviewTemplate> findByIdAndPublishedAtIsNotNullAndArchivedAtIsNull(Long id);

    long countByPublishedAtIsNotNullAndArchivedAtIsNull();

    @Query("""
            SELECT template
            FROM InterviewTemplate template
            LEFT JOIN FETCH template.sourceJobDescription
            WHERE template.id = :id
              AND (template.owner.id = :userId
                   OR (template.publishedAt IS NOT NULL AND template.archivedAt IS NULL))
            """)
    Optional<InterviewTemplate> findAccessibleForSession(
            @Param("id") Long id,
            @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM InterviewTemplate t WHERE t.id = :id AND t.owner.id = :ownerId")
    Optional<InterviewTemplate> findOwnedForUpdate(@Param("id") Long id,
                                                   @Param("ownerId") Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT template FROM InterviewTemplate template WHERE template.id = :id")
    Optional<InterviewTemplate> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT template
            FROM InterviewTemplate template
            JOIN FETCH template.owner owner
            WHERE (:keyword IS NULL
                   OR LOWER(template.title) LIKE :keyword
                   OR LOWER(template.jobTitle) LIKE :keyword
                   OR LOWER(owner.fullName) LIKE :keyword
                   OR LOWER(owner.email) LIKE :keyword)
              AND (:ownerId IS NULL OR owner.id = :ownerId)
              AND (:moderationStatus IS NULL OR template.moderationStatus = :moderationStatus)
              AND (:published IS NULL
                   OR (:published = TRUE AND template.publishedAt IS NOT NULL)
                   OR (:published = FALSE AND template.publishedAt IS NULL))
              AND (:featured IS NULL OR template.featured = :featured)
              AND (:category IS NULL OR LOWER(template.category) = :category)
            """,
            countQuery = """
                    SELECT COUNT(template)
                    FROM InterviewTemplate template
                    JOIN template.owner owner
                    WHERE (:keyword IS NULL
                           OR LOWER(template.title) LIKE :keyword
                           OR LOWER(template.jobTitle) LIKE :keyword
                           OR LOWER(owner.fullName) LIKE :keyword
                           OR LOWER(owner.email) LIKE :keyword)
                      AND (:ownerId IS NULL OR owner.id = :ownerId)
                      AND (:moderationStatus IS NULL OR template.moderationStatus = :moderationStatus)
                      AND (:published IS NULL
                           OR (:published = TRUE AND template.publishedAt IS NOT NULL)
                           OR (:published = FALSE AND template.publishedAt IS NULL))
                      AND (:featured IS NULL OR template.featured = :featured)
                      AND (:category IS NULL OR LOWER(template.category) = :category)
                    """)
    Page<InterviewTemplate> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("ownerId") Long ownerId,
            @Param("moderationStatus") TemplateModerationStatus moderationStatus,
            @Param("published") Boolean published,
            @Param("featured") Boolean featured,
            @Param("category") String category,
            Pageable pageable);
}
