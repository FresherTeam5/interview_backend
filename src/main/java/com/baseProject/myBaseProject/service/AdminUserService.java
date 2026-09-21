package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateUserStatusRequest;
import com.baseProject.myBaseProject.dto.admin.UpdateUserAccessRequest;
import com.baseProject.myBaseProject.dto.admin.AdminUserOperationsSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSecurityResponse;
import com.baseProject.myBaseProject.enums.AccountAccessStatus;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.enums.UserRole;

import java.time.Instant;

public interface AdminUserService {
    AdminPageResponse<AdminUserSummaryResponse> list(
            String keyword, UserRole role, Boolean enabled, int page, int size);

    AdminUserDetailResponse get(Long userId);

    AdminUserDetailResponse updateStatus(
            Long adminId, Long userId, UpdateUserStatusRequest request);

    AdminPageResponse<AdminUserOperationsSummaryResponse> listAdvanced(
            String keyword, Boolean emailVerified, AccountAccessStatus accessStatus,
            AccountDeletionStatus deletionStatus, Instant lastLoginFrom, Instant lastLoginTo,
            int page, int size);

    AdminUserSecurityResponse getSecurity(Long userId);

    AdminUserSecurityResponse updateAccess(
            Long adminId, Long userId, UpdateUserAccessRequest request);

    int revokeSessions(Long adminId, Long userId);

    void sendVerification(Long adminId, Long userId);

    void sendPasswordReset(Long adminId, Long userId);
}
