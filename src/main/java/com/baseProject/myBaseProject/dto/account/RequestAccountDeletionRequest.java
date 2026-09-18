package com.baseProject.myBaseProject.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestAccountDeletionRequest(
        @NotBlank @Size(max = 20) String confirmation,
        @Size(max = 100) String currentPassword) {
}
