package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;

public record AdminTemplateDetailResponse(
        InterviewTemplateResponse template,
        AdminUserReferenceResponse owner,
        AdminUserReferenceResponse reviewedBy,
        long totalViews,
        long favoriteCount,
        long interviewSessionCount) {
}
