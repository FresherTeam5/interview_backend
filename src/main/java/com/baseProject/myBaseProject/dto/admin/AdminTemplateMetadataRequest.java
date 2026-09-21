package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminTemplateMetadataRequest(
        @Size(max = 80) String category,
        @Size(max = 10) List<@Size(max = 40) String> tags,
        boolean featured,
        @Min(0) @Max(100000) int displayOrder,
        @Min(0) long expectedVersion) {
}
