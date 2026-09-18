package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.feedback.InterviewFeedbackResponse;
import com.baseProject.myBaseProject.dto.feedback.UpsertInterviewFeedbackRequest;

public interface InterviewFeedbackService {
    InterviewFeedbackResponse get(Long userId, Long sessionId);

    InterviewFeedbackResponse upsert(
            Long userId, Long sessionId, UpsertInterviewFeedbackRequest request);
}
