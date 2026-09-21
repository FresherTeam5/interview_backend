package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSystemSettingRequest(
        @NotBlank @Size(max = 1000) String value,
        @Min(0) long expectedVersion) {
}
