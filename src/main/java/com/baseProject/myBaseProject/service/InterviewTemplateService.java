package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.TemplatePageResponse;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.CloneInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.TemplateFavoriteResponse;

import java.util.List;

public interface InterviewTemplateService {
    InterviewTemplateResponse get(Long userId, Long id);

    TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            Long userId, String scope, int page, int size);

    TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            Long userId, String scope, String keyword, String seniority,
            String language, String technology, int page, int size);

    List<InterviewTemplateResponse> listOwnedDetails(Long userId);

    InterviewTemplateResponse cloneTemplate(
            Long userId, Long id, CloneInterviewTemplateRequest request);

    TemplateFavoriteResponse favorite(Long userId, Long id);

    TemplateFavoriteResponse unfavorite(Long userId, Long id);

    InterviewTemplateResponse update(Long userId, Long id, UpdateInterviewTemplateRequest request);

    InterviewTemplateResponse confirm(Long userId, Long id, long expectedVersion);

    InterviewTemplateResponse publish(Long userId, Long id, long expectedVersion);

    InterviewTemplateResponse unpublish(Long userId, Long id, long expectedVersion);

    InterviewTemplateResponse archive(Long userId, Long id, long expectedVersion);
}
