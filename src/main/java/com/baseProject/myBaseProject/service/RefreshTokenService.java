package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.dto.account.ClientMetadata;
import com.baseProject.myBaseProject.dto.account.LoginSessionResponse;

import java.util.List;

public interface RefreshTokenService {
    String issue(UserAccount user);
    String issue(UserAccount user, ClientMetadata metadata);
    RotationResult rotate(String rawToken);
    RotationResult rotate(String rawToken, ClientMetadata metadata);
    void revoke(String rawToken);
    int revokeAllForUser(Long userId);
    int purgeExpired();
    List<LoginSessionResponse> listSessions(Long userId, String currentRawToken);
    void revokeSession(Long userId, String familyId);

    record RotationResult(UserAccount user, String refreshToken) {}
}
