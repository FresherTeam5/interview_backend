package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewAssessment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InterviewAssessmentRepository extends JpaRepository<InterviewAssessment, Long> {
    Optional<InterviewAssessment> findBySessionId(Long sessionId);

    @EntityGraph(attributePaths = "session")
    List<InterviewAssessment> findBySessionIdIn(List<Long> sessionIds);

    @EntityGraph(attributePaths = "session")
    @Query("""
            SELECT assessment
            FROM InterviewAssessment assessment
            JOIN assessment.session session
            WHERE session.user.id = :userId
              AND session.status = com.baseProject.myBaseProject.enums.InterviewSessionStatus.COMPLETED
              AND session.completedAt >= :completedFrom
              AND session.completedAt < :completedTo
            ORDER BY session.completedAt ASC
            """)
    List<InterviewAssessment> findCompletedForUserBetween(
            @Param("userId") Long userId,
            @Param("completedFrom") Instant completedFrom,
            @Param("completedTo") Instant completedTo);
}
