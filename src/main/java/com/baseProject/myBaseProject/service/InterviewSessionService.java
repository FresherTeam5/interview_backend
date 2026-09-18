package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;

public interface InterviewSessionService {
    InterviewSessionOptionsResponse options();

    InterviewSessionStatusResponse create(
            Long userId, String idempotencyKey, CreateInterviewSessionRequest request);

    InterviewSessionStatusResponse get(Long userId, Long sessionId);

    InterviewSessionStatusResponse retryPreparation(Long userId, Long sessionId);

    InterviewSessionStatusResponse cancel(Long userId, Long sessionId);

    void retryPreparationForAdmin(Long sessionId);
}
