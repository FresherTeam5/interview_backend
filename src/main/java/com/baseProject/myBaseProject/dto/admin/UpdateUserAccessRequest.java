package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AccountAccessStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateUserAccessRequest(
        @NotNull AccountAccessStatus status,
        @Size(max = 500) String reason,
        Instant suspendedUntil) {
}
