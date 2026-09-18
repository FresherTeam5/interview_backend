package com.baseProject.myBaseProject.dto.account;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @Size(max = 150) String fullName,
        @Size(max = 1000)
        @Pattern(regexp = "https?://.+", message = "avatarUrl must use http or https")
        String avatarUrl,
        Boolean clearAvatar,
        @Pattern(regexp = "[a-z]{2,3}([_-][A-Za-z]{2,8})?",
                message = "languageCode is invalid")
        String languageCode,
        @Size(max = 50) String timeZone,
        Boolean emailNotifications,
        Boolean processingNotifications) {
}
