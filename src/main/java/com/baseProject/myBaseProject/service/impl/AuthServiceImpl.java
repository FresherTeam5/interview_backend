package com.baseProject.myBaseProject.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.auth.AuthResponse;
import com.baseProject.myBaseProject.dto.auth.AuthResult;
import com.baseProject.myBaseProject.dto.auth.CurrentUserResponse;
import com.baseProject.myBaseProject.dto.auth.GoogleLoginRequest;
import com.baseProject.myBaseProject.dto.auth.GoogleUserInfo;
import com.baseProject.myBaseProject.dto.auth.LoginRequest;
import com.baseProject.myBaseProject.dto.auth.RegisterRequest;
import com.baseProject.myBaseProject.dto.account.ClientMetadata;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.GoogleIdTokenVerifier;
import com.baseProject.myBaseProject.service.AuthService;
import com.baseProject.myBaseProject.service.JwtService;
import com.baseProject.myBaseProject.service.RefreshTokenService;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final Clock clock;

    @Transactional
    public AuthResult register(RegisterRequest request, ClientMetadata metadata) {
        String email = request.email().trim().toLowerCase();

        if (userAccountRepository.existsByEmail(email)) {
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL);
        }

        Instant now = clock.instant();
        UserAccount account = UserAccount.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        account = userAccountRepository.save(account);

        return issueTokens(new CustomUserDetails(account), account, metadata);
    }

    @Transactional
    public AuthResult login(LoginRequest request, ClientMetadata metadata) {
        String email = request.email().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // create a userAccout only id has value to create a foreign key
        UserAccount accountRef = userAccountRepository.getReferenceById(userDetails.getId());

        return issueTokens(userDetails, accountRef, metadata);
    }

    @Transactional
    public AuthResult loginWithGoogle(GoogleLoginRequest request, ClientMetadata metadata) {
        GoogleUserInfo googleUser = googleIdTokenVerifier.verify(request.idToken());

        UserAccount account = userAccountRepository.findByGoogleId(googleUser.googleId())
                .map(existing -> syncProfile(existing, googleUser))
                .orElseGet(() -> userAccountRepository.findByEmail(googleUser.email())
                        .map(existing -> linkGoogleAccount(existing, googleUser))
                        .orElseGet(() -> createFromGoogle(googleUser)));


        if (!account.isEnabled()) {
            throw new DisabledException(Message.ACCOUNT_DISABLED);
        }

        account = userAccountRepository.save(account);
        return issueTokens(new CustomUserDetails(account), account, metadata);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, Message.USER_NOT_FOUND));

        return new CurrentUserResponse(
                account.getId(),
                account.getFullName(),
                account.getEmail(),
                account.getAvatarUrl(),
                account.getRole(),
                account.getEmailVerifiedAt(),
                account.getDeletionRequestedAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private UserAccount syncProfile(UserAccount account, GoogleUserInfo googleUser) {
        account.setFullName(googleUser.fullName());
        account.setAvatarUrl(googleUser.avatarUrl());
        account.setUpdatedAt(clock.instant());
        return account;
    }

    private UserAccount linkGoogleAccount(UserAccount account, GoogleUserInfo googleUser) {
        account.setGoogleId(googleUser.googleId());
        account.setEmailVerifiedAt(clock.instant());

        if (account.getAvatarUrl() == null) {
            account.setAvatarUrl(googleUser.avatarUrl());
        }
        account.setUpdatedAt(clock.instant());
        return account;
    }

    private UserAccount createFromGoogle(GoogleUserInfo googleUser) {
        Instant now = clock.instant();
        return UserAccount.builder()
                .fullName(googleUser.fullName())
                .email(googleUser.email())
                .googleId(googleUser.googleId())
                .avatarUrl(googleUser.avatarUrl())
                .passwordHash(null)
                .role(UserRole.USER)
                .enabled(true)
                .emailVerifiedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Transactional
    public AuthResult refresh(String refreshToken, ClientMetadata metadata) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(
                refreshToken, metadata);

        CustomUserDetails userDetails = new CustomUserDetails(rotation.user());
        String accessToken = jwtService.generateAccessToken(userDetails);

        return toResult(accessToken, rotation.refreshToken(), userDetails);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public int logoutAll(Long userId) {
        return refreshTokenService.revokeAllForUser(userId);
    }

    //create token and response
    private AuthResult issueTokens(
            CustomUserDetails userDetails,
            UserAccount accountRef,
            ClientMetadata metadata) {
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.issue(accountRef, metadata);

        return toResult(accessToken, refreshToken, userDetails);
    }

    private AuthResult toResult(String accessToken, String refreshToken, CustomUserDetails userDetails) {
        AuthResponse body = new AuthResponse(
                accessToken,
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getRole()
        );
        return new AuthResult(body, refreshToken);
    }
}
