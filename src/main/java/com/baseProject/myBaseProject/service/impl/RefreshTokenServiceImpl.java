package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.RefreshTokenProperties;
import com.baseProject.myBaseProject.dto.account.ClientMetadata;
import com.baseProject.myBaseProject.dto.account.LoginSessionResponse;
import com.baseProject.myBaseProject.entity.RefreshToken;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.RefreshTokenRepository;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import com.baseProject.myBaseProject.util.Sha256;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String issue(UserAccount user) {
        return issue(user, ClientMetadata.unknown());
    }

    @Override
    @Transactional
    public String issue(UserAccount user, ClientMetadata metadata) {
        return persist(user, UUID.randomUUID().toString(), clock.instant(), metadata);
    }

    @Override
    @Transactional
    public RotationResult rotate(String rawToken) {
        return rotate(rawToken, ClientMetadata.unknown());
    }

    @Override
    @Transactional
    public RotationResult rotate(String rawToken, ClientMetadata metadata) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (stored.isExpiredAt(now)) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserAccount user = stored.getUser();
        if (!user.canAuthenticateAt(now)) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        stored.setRevokedAt(now);
        stored.setLastUsedAt(now);
        String newRefreshtoken = persist(user, stored.getFamilyId(), now, metadata);

        return new RotationResult(user, newRefreshtoken);
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Override
    @Transactional
    public int revokeAllForUser(Long userId) {
        return refreshTokenRepository.revokeAllByUserId(userId, clock.instant());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteAllExpiredBefore(clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginSessionResponse> listSessions(Long userId, String currentRawToken) {
        String currentFamily = currentRawToken == null
                ? null
                : refreshTokenRepository.findByTokenHash(hash(currentRawToken))
                        .filter(token -> token.getUser().getId().equals(userId))
                        .map(RefreshToken::getFamilyId)
                        .orElse(null);
        return refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(
                        userId, clock.instant())
                .stream()
                .map(token -> new LoginSessionResponse(
                        token.getFamilyId(),
                        deviceName(token.getUserAgent()),
                        token.getUserAgent(),
                        token.getIpAddress(),
                        token.getIssuedAt(),
                        token.getLastUsedAt(),
                        token.getExpiresAt(),
                        token.getFamilyId().equals(currentFamily)))
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(Long userId, String familyId) {
        if (!refreshTokenRepository.existsByUserIdAndFamilyId(userId, familyId)) {
            throw new DomainException(ErrorCode.DEVICE_SESSION_NOT_FOUND);
        }
        refreshTokenRepository.revokeFamilyForUser(userId, familyId, clock.instant());
    }

    private String persist(
            UserAccount user,
            String familyId,
            Instant now,
            ClientMetadata metadata) {
        String rawToken = generateRawToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .familyId(familyId)
                .user(user)
                .issuedAt(now)
                .expiresAt(now.plusMillis(properties.expirationMs()))
                .userAgent(truncate(metadata.userAgent(), 500))
                .ipAddress(truncate(metadata.ipAddress(), 64))
                .lastUsedAt(now)
                .build());

        return rawToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        return Sha256.hex(rawToken.getBytes(StandardCharsets.UTF_8));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private String deviceName(String userAgent) {
        if (userAgent == null) {
            return "Unknown device";
        }
        String value = userAgent.toLowerCase(Locale.ROOT);
        String browser = value.contains("edg/") ? "Edge"
                : value.contains("chrome/") ? "Chrome"
                : value.contains("firefox/") ? "Firefox"
                : value.contains("safari/") ? "Safari"
                : "Browser";
        String system = value.contains("android") ? "Android"
                : value.contains("iphone") || value.contains("ipad") ? "iOS"
                : value.contains("windows") ? "Windows"
                : value.contains("mac os") ? "macOS"
                : value.contains("linux") ? "Linux"
                : "Unknown OS";
        return browser + " on " + system;
    }
}
