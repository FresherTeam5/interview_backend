package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.account.AccountDeletionResponse;
import com.baseProject.myBaseProject.dto.account.AccountProfileResponse;
import com.baseProject.myBaseProject.dto.account.LoginSessionResponse;
import com.baseProject.myBaseProject.dto.account.RequestAccountDeletionRequest;
import com.baseProject.myBaseProject.dto.account.UpdateAccountRequest;

import java.util.List;
import java.util.Optional;

public interface AccountService {
    AccountProfileResponse get(Long userId);

    AccountProfileResponse update(Long userId, UpdateAccountRequest request);

    List<LoginSessionResponse> loginSessions(Long userId, String currentRefreshToken);

    void revokeLoginSession(Long userId, String sessionId);

    AccountDeletionResponse requestDeletion(
            Long userId, RequestAccountDeletionRequest request);

    Optional<AccountDeletionResponse> deletionStatus(Long userId);

    void cancelDeletion(Long userId);
}
