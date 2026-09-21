package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminAnnouncementRequest;
import com.baseProject.myBaseProject.dto.admin.AdminAnnouncementResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AnnouncementAudiencePreviewResponse;
import com.baseProject.myBaseProject.dto.admin.ScheduleAnnouncementRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateAdminAnnouncementRequest;
import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;

public interface AdminAnnouncementService {
    AdminPageResponse<AdminAnnouncementResponse> list(
            AnnouncementStatus status, int page, int size);

    AdminAnnouncementResponse get(Long id);

    AdminAnnouncementResponse create(Long adminId, AdminAnnouncementRequest request);

    AdminAnnouncementResponse update(
            Long adminId, Long id, UpdateAdminAnnouncementRequest request);

    AdminAnnouncementResponse schedule(
            Long adminId, Long id, ScheduleAnnouncementRequest request);

    AdminAnnouncementResponse cancel(Long adminId, Long id, long expectedVersion);

    AnnouncementAudiencePreviewResponse preview(AnnouncementAudience audience);
}
