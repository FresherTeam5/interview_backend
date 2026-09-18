package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    Optional<InterviewFeedback> findBySessionIdAndUserId(Long sessionId, Long userId);
}
