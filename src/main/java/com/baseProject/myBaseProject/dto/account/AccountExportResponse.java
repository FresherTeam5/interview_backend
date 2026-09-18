package com.baseProject.myBaseProject.dto.account;

import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionAnalysisResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.InterviewReportResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;

import java.time.Instant;
import java.util.List;

public record AccountExportResponse(
        Instant generatedAt,
        AccountProfileResponse account,
        List<CvDocumentResponse> cvs,
        List<CandidateProfileResponse> profiles,
        List<JobDescriptionData> jobDescriptions,
        List<InterviewTemplateResponse> templates,
        List<InterviewData> interviews) {

    public record JobDescriptionData(
            JobDescriptionResponse document,
            JobDescriptionAnalysisResponse analysis) {
    }

    public record InterviewData(
            InterviewSessionSummaryResponse session,
            InterviewConversationResponse conversation,
            InterviewReportResponse report) {
    }
}
