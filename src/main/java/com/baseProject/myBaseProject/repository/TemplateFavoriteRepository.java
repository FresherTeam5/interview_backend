package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.TemplateFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateFavoriteRepository extends JpaRepository<TemplateFavorite, Long> {
    boolean existsByUserIdAndTemplateId(Long userId, Long templateId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO user_template_favorites(user_id, template_id, created_at)
            VALUES (:userId, :templateId, :createdAt)
            """, nativeQuery = true)
    int addForUser(@Param("userId") Long userId,
                   @Param("templateId") Long templateId,
                   @Param("createdAt") java.time.Instant createdAt);

    @Modifying
    @Query("DELETE FROM TemplateFavorite favorite WHERE favorite.user.id = :userId AND favorite.template.id = :templateId")
    int deleteForUser(@Param("userId") Long userId, @Param("templateId") Long templateId);

    @Query("""
            SELECT favorite.template
            FROM TemplateFavorite favorite
            WHERE favorite.user.id = :userId
              AND favorite.template.archivedAt IS NULL
              AND (favorite.template.owner.id = :userId
                   OR favorite.template.publishedAt IS NOT NULL)
              AND (:keyword IS NULL
                   OR LOWER(favorite.template.title) LIKE :keyword
                   OR LOWER(favorite.template.jobTitle) LIKE :keyword
                   OR LOWER(favorite.template.contentJson) LIKE :keyword)
              AND (:seniority IS NULL OR LOWER(favorite.template.targetSeniority) = :seniority)
              AND (:language IS NULL OR LOWER(favorite.template.contentJson) LIKE :language)
              AND (:technology IS NULL OR LOWER(favorite.template.contentJson) LIKE :technology)
            """)
    Page<InterviewTemplate> searchFavorites(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("seniority") String seniority,
            @Param("language") String language,
            @Param("technology") String technology,
            Pageable pageable);
}
