package com.baseProject.myBaseProject.dto.session;

import java.util.List;

public record InterviewSessionPageResponse(
        List<InterviewSessionSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
