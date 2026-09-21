package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewTemplateMapper {
    private final JobAnalysisJsonMapper analysisJsonMapper;

    public InterviewTemplateResponse toResponse(InterviewTemplate template) {
        return new InterviewTemplateResponse(
                template.getId(), sourceId(template), template.getTitle(),
                template.getJobTitle(), template.getTargetSeniority(),
                analysisJsonMapper.fromJson(template.getContentJson()), template.isConfirmed(),
                template.getConfirmedAt(), template.isPublished(), template.getPublishedAt(),
                template.getModerationStatus(), template.getSubmittedAt(), template.getReviewedAt(),
                template.getModerationReason(), template.getCategory(), template.getTagsJson(),
                template.isFeatured(), template.getDisplayOrder(),
                template.getArchivedAt(), template.getVersion(), template.getCreatedAt(),
                template.getUpdatedAt());
    }

    public InterviewTemplateSummaryResponse toSummary(InterviewTemplate template) {
        return new InterviewTemplateSummaryResponse(
                template.getId(), sourceId(template), template.getTitle(),
                template.getJobTitle(), template.getTargetSeniority(), template.isConfirmed(),
                template.isPublished(), template.getModerationStatus(),
                template.getModerationReason(), template.getCategory(), template.getTagsJson(),
                template.isFeatured(), template.getArchivedAt(), template.getUpdatedAt());
    }

    private Long sourceId(InterviewTemplate template) {
        return template.getSourceJobDescription() == null
                ? null : template.getSourceJobDescription().getId();
    }
}
