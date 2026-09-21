package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateMetadataRequest;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateReviewRequest;
import com.baseProject.myBaseProject.dto.admin.AdminTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.TemplateVersionRequest;
import com.baseProject.myBaseProject.enums.TemplateModerationStatus;

public interface AdminTemplateModerationService {
    AdminPageResponse<AdminTemplateSummaryResponse> list(
            String keyword, Long ownerId, TemplateModerationStatus status,
            Boolean published, Boolean featured, String category, int page, int size);

    AdminTemplateDetailResponse get(Long templateId);

    AdminTemplateDetailResponse review(
            Long adminId, Long templateId, AdminTemplateReviewRequest request);

    AdminTemplateDetailResponse updateMetadata(
            Long adminId, Long templateId, AdminTemplateMetadataRequest request);

    AdminTemplateDetailResponse publish(
            Long adminId, Long templateId, TemplateVersionRequest request);

    AdminTemplateDetailResponse unpublish(
            Long adminId, Long templateId, TemplateVersionRequest request);
}
