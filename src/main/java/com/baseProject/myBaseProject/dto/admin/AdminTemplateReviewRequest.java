package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.TemplateModerationAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminTemplateReviewRequest(
        @NotNull TemplateModerationAction action,
        @Size(max = 1000) String reason,
        @Min(0) long expectedVersion) {
}
