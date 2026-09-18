package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewReadinessResponse;

public interface InterviewReadinessService {
    InterviewReadinessResponse check(Long userId, CreateInterviewSessionRequest request);
}
