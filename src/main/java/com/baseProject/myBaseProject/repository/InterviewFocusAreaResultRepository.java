package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InterviewFocusAreaResultRepository
        extends JpaRepository<InterviewFocusAreaResult, Long> {
    List<InterviewFocusAreaResult> findByAssessmentIdOrderByFocusAreaDisplayOrderAsc(
            Long assessmentId);

    @Query("""
            SELECT result
            FROM InterviewFocusAreaResult result
            JOIN FETCH result.focusArea focusArea
            JOIN result.assessment assessment
            JOIN assessment.session session
            WHERE session.user.id = :userId
              AND session.status = com.baseProject.myBaseProject.enums.InterviewSessionStatus.COMPLETED
              AND session.completedAt >= :completedFrom
              AND session.completedAt < :completedTo
              AND result.score IS NOT NULL
            """)
    List<InterviewFocusAreaResult> findScoredForUserCompletedBetween(
            @Param("userId") Long userId,
            @Param("completedFrom") Instant completedFrom,
            @Param("completedTo") Instant completedTo);
}
