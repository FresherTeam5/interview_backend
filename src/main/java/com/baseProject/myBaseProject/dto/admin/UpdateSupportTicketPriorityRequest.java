package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.SupportTicketPriority;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketPriorityRequest(@NotNull SupportTicketPriority priority) {
}
