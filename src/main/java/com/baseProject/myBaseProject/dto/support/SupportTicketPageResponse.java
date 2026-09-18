package com.baseProject.myBaseProject.dto.support;

import java.util.List;

public record SupportTicketPageResponse(
        List<SupportTicketResponse> content,
        int page,
        int size,
        long totalElements) {
}
