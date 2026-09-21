package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    Optional<InterviewFeedback> findBySessionIdAndUserId(Long sessionId, Long userId);

    @Query(value = """
            SELECT feedback
            FROM InterviewFeedback feedback
            JOIN FETCH feedback.user user
            JOIN FETCH feedback.session session
            WHERE (:keyword IS NULL
                   OR LOWER(user.fullName) LIKE :keyword
                   OR LOWER(user.email) LIKE :keyword
                   OR LOWER(feedback.comment) LIKE :keyword)
              AND (:rating IS NULL
                   OR feedback.questionRating = :rating
                   OR feedback.voiceRating = :rating
                   OR feedback.reportRating = :rating)
              AND (:createdFrom IS NULL OR feedback.createdAt >= :createdFrom)
              AND (:createdTo IS NULL OR feedback.createdAt <= :createdTo)
            """,
            countQuery = """
                    SELECT COUNT(feedback)
                    FROM InterviewFeedback feedback
                    JOIN feedback.user user
                    WHERE (:keyword IS NULL
                           OR LOWER(user.fullName) LIKE :keyword
                           OR LOWER(user.email) LIKE :keyword
                           OR LOWER(feedback.comment) LIKE :keyword)
                      AND (:rating IS NULL
                           OR feedback.questionRating = :rating
                           OR feedback.voiceRating = :rating
                           OR feedback.reportRating = :rating)
                      AND (:createdFrom IS NULL OR feedback.createdAt >= :createdFrom)
                      AND (:createdTo IS NULL OR feedback.createdAt <= :createdTo)
                    """)
    Page<InterviewFeedback> searchForAdmin(
            @Param("keyword") String keyword,
            @Param("rating") Integer rating,
            @Param("createdFrom") Instant from,
            @Param("createdTo") Instant to,
            Pageable pageable);
}
