package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewSessionTransitionRepository
        extends JpaRepository<InterviewSessionTransition, Long> {
    List<InterviewSessionTransition> findBySessionIdOrderByOccurredAtAsc(Long sessionId);

    long countBySessionIdAndActorAndToStatus(
            Long sessionId, InterviewTransitionActor actor, InterviewSessionStatus toStatus);
}
