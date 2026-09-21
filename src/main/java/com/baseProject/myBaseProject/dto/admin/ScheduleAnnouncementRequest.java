package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleAnnouncementRequest(
        @NotNull Instant scheduledAt,
        @Min(0) long expectedVersion) {
}
