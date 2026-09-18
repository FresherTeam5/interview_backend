package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.account.ClientMetadata;
import com.baseProject.myBaseProject.dto.auth.AuthResult;
import com.baseProject.myBaseProject.dto.auth.CurrentUserResponse;
import com.baseProject.myBaseProject.dto.auth.GoogleLoginRequest;
import com.baseProject.myBaseProject.dto.auth.LoginRequest;
import com.baseProject.myBaseProject.dto.auth.RegisterRequest;

public interface AuthService {
    AuthResult register(RegisterRequest request, ClientMetadata metadata);
    AuthResult login(LoginRequest request, ClientMetadata metadata);
    AuthResult loginWithGoogle(GoogleLoginRequest request, ClientMetadata metadata);
    CurrentUserResponse currentUser(Long userId);
    AuthResult refresh(String refreshToken, ClientMetadata metadata);
    void logout(String refreshToken);
    int logoutAll(Long userId);
}
