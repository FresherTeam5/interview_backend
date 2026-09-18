package com.baseProject.myBaseProject.service;

public interface AccountCredentialService {
    void requestEmailVerification(Long userId);

    void verifyEmail(String rawToken);

    void requestPasswordReset(String email);

    void resetPassword(String rawToken, String newPassword);

    void changePassword(Long userId, String currentPassword, String newPassword);

    int purgeExpiredTokens();
}
