package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAddSupportMessageRequest(
        @NotBlank @Size(max = 5000) String message,
        boolean internal) {
}
