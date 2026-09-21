package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AnnouncementAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String message,
        @NotNull AnnouncementAudience audience,
        boolean inAppEnabled,
        boolean emailEnabled) {
}
