package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminAddSupportMessageRequest;
import com.baseProject.myBaseProject.dto.admin.AdminInterviewFeedbackResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSupportTicketSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AssignSupportTicketRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketPriorityRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateSupportTicketStatusRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;
import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import com.baseProject.myBaseProject.enums.SupportTicketType;

import java.time.Instant;

public interface AdminSupportService {
    AdminPageResponse<AdminSupportTicketSummaryResponse> list(
            String keyword, SupportTicketStatus status, SupportTicketType type,
            SupportTicketPriority priority, Long assignedAdminId,
            Instant from, Instant to, int page, int size);

    AdminSupportTicketDetailResponse get(Long ticketId);

    AdminSupportTicketDetailResponse assign(
            Long actorId, Long ticketId, AssignSupportTicketRequest request);

    AdminSupportTicketDetailResponse updatePriority(
            Long actorId, Long ticketId, UpdateSupportTicketPriorityRequest request);

    AdminSupportTicketDetailResponse updateStatus(
            Long actorId, Long ticketId, UpdateSupportTicketStatusRequest request);

    SupportTicketMessageResponse addMessage(
            Long actorId, Long ticketId, AdminAddSupportMessageRequest request);

    AdminPageResponse<AdminInterviewFeedbackResponse> listFeedback(
            String keyword, Integer rating, Instant from, Instant to, int page, int size);
}
