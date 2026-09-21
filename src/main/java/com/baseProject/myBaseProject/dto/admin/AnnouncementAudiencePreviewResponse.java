package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.AnnouncementAudience;

public record AnnouncementAudiencePreviewResponse(
        AnnouncementAudience audience,
        long eligibleRecipients) {
}
