package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSupportTicketStatusRequest(
        @NotNull SupportTicketStatus status,
        @Size(max = 2000) String resolutionSummary) {
}
