package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.account.AccountExportResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionAnalysisResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.session.InterviewReportResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionPageResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.service.AccountExportService;
import com.baseProject.myBaseProject.service.AccountService;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import com.baseProject.myBaseProject.service.CvDocumentService;
import com.baseProject.myBaseProject.service.InterviewConversationService;
import com.baseProject.myBaseProject.service.InterviewHistoryService;
import com.baseProject.myBaseProject.service.InterviewReportService;
import com.baseProject.myBaseProject.service.InterviewTemplateService;
import com.baseProject.myBaseProject.service.JobDescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountExportServiceImpl implements AccountExportService {
    private static final int PAGE_SIZE = 100;

    private final AccountService accounts;
    private final CvDocumentService cvs;
    private final CandidateProfileService profiles;
    private final JobDescriptionService jobDescriptions;
    private final InterviewTemplateService templates;
    private final InterviewHistoryService history;
    private final InterviewConversationService conversations;
    private final InterviewReportService reports;
    private final Clock clock;

    @Override
    public AccountExportResponse export(Long userId) {
        List<CandidateProfileResponse> profileData = profiles.list(userId).stream()
                .map(summary -> profiles.get(userId, summary.id()))
                .toList();
        List<JobDescriptionResponse> jobDocuments = jobDescriptions.list(userId);
        List<AccountExportResponse.JobDescriptionData> jobData = jobDocuments.stream()
                .map(document -> new AccountExportResponse.JobDescriptionData(
                        document,
                        analysisIfReady(userId, document)))
                .toList();
        List<InterviewTemplateResponse> templateData = templates.listOwnedDetails(userId);

        List<InterviewSessionSummaryResponse> sessionData = allSessions(userId);
        List<AccountExportResponse.InterviewData> interviewData = sessionData.stream()
                .map(session -> new AccountExportResponse.InterviewData(
                        session,
                        conversations.get(userId, session.id()),
                        reportIfCompleted(userId, session)))
                .toList();

        return new AccountExportResponse(
                clock.instant(),
                accounts.get(userId),
                cvs.list(userId),
                profileData,
                jobData,
                templateData,
                interviewData);
    }

    private JobDescriptionAnalysisResponse analysisIfReady(
            Long userId, JobDescriptionResponse document) {
        return document.status() == JobDescriptionStatus.READY
                ? jobDescriptions.analysis(userId, document.id())
                : null;
    }

    private InterviewReportResponse reportIfCompleted(
            Long userId, InterviewSessionSummaryResponse session) {
        return session.status() == InterviewSessionStatus.COMPLETED
                ? reports.get(userId, session.id())
                : null;
    }

    private List<InterviewSessionSummaryResponse> allSessions(Long userId) {
        List<InterviewSessionSummaryResponse> values = new ArrayList<>();
        int page = 0;
        while (true) {
            InterviewSessionPageResponse response = history.list(
                    userId, null, null, null, null, null, page, PAGE_SIZE);
            values.addAll(response.items());
            if (page + 1 >= response.totalPages()) {
                return List.copyOf(values);
            }
            page++;
        }
    }
}
