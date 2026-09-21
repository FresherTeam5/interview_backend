package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminBulkSessionRequest;
import com.baseProject.myBaseProject.dto.admin.AdminBulkSessionResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionActionRequest;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDiagnosticsResponse;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

import java.time.Instant;

public interface AdminInterviewSessionService {
    AdminPageResponse<AdminSessionSummaryResponse> list(
            String keyword,
            InterviewSessionStatus status,
            InterviewSessionMode mode,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size);

    AdminSessionDetailResponse get(Long sessionId);

    AdminSessionDetailResponse retryPreparation(Long adminId, Long sessionId);

    AdminSessionDetailResponse retryScoring(Long adminId, Long sessionId);

    AdminPageResponse<AdminSessionSummaryResponse> listStale(
            InterviewSessionStatus status, int staleMinutes, int page, int size);

    AdminSessionDiagnosticsResponse diagnostics(Long sessionId);

    AdminSessionDetailResponse forceClose(
            Long adminId, Long sessionId, AdminSessionActionRequest request);

    AdminSessionDetailResponse terminate(
            Long adminId, Long sessionId, AdminSessionActionRequest request);

    AdminBulkSessionResponse bulkRetryPreparation(
            Long adminId, AdminBulkSessionRequest request);

    AdminBulkSessionResponse bulkRetryScoring(
            Long adminId, AdminBulkSessionRequest request);
}
