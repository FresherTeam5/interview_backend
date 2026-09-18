package com.baseProject.myBaseProject.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmAccountTokenRequest(
        @NotBlank
        @Size(max = 200)
        String token) {
}
