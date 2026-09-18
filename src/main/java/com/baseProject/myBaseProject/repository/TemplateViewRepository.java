package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.TemplateView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TemplateViewRepository extends JpaRepository<TemplateView, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO user_template_views(user_id, template_id, view_count, last_viewed_at)
            VALUES (:userId, :templateId, 1, :viewedAt)
            ON DUPLICATE KEY UPDATE view_count = view_count + 1, last_viewed_at = :viewedAt
            """, nativeQuery = true)
    void recordView(@Param("userId") Long userId,
                    @Param("templateId") Long templateId,
                    @Param("viewedAt") Instant viewedAt);

    @Query("""
            SELECT viewed.template
            FROM TemplateView viewed
            WHERE viewed.user.id = :userId
              AND viewed.template.archivedAt IS NULL
              AND (viewed.template.owner.id = :userId
                   OR viewed.template.publishedAt IS NOT NULL)
              AND (:keyword IS NULL
                   OR LOWER(viewed.template.title) LIKE :keyword
                   OR LOWER(viewed.template.jobTitle) LIKE :keyword
                   OR LOWER(viewed.template.contentJson) LIKE :keyword)
              AND (:seniority IS NULL OR LOWER(viewed.template.targetSeniority) = :seniority)
              AND (:language IS NULL OR LOWER(viewed.template.contentJson) LIKE :language)
              AND (:technology IS NULL OR LOWER(viewed.template.contentJson) LIKE :technology)
            ORDER BY viewed.lastViewedAt DESC
            """)
    Page<InterviewTemplate> searchRecent(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("seniority") String seniority,
            @Param("language") String language,
            @Param("technology") String technology,
            Pageable pageable);
}
