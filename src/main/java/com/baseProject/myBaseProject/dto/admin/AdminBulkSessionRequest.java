package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminBulkSessionRequest(
        @NotEmpty @Size(max = 100) List<Long> sessionIds) {
}
