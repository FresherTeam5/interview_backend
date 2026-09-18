package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.session.InterviewProgressResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionPageResponse;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.time.Instant;

public interface InterviewHistoryService {
    InterviewSessionPageResponse list(
            Long userId,
            String keyword,
            InterviewSessionStatus status,
            InterviewSessionMode mode,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size);

    InterviewProgressResponse progress(Long userId, int days);
}
