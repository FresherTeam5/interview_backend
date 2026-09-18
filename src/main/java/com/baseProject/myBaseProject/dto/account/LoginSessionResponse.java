package com.baseProject.myBaseProject.dto.account;

import java.time.Instant;

public record LoginSessionResponse(
        String id,
        String deviceName,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean current) {
}
