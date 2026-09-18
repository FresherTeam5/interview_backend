package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.support.CreateSupportTicketRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketPageResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketResponse;

public interface SupportTicketService {
    SupportTicketResponse create(Long userId, CreateSupportTicketRequest request);

    SupportTicketPageResponse list(Long userId, int page, int size);

    SupportTicketResponse get(Long userId, Long ticketId);
}
