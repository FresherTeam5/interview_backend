package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.support.CreateSupportTicketRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketPageResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketResponse;
import com.baseProject.myBaseProject.dto.support.CreateSupportMessageRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;

import java.util.List;

public interface SupportTicketService {
    SupportTicketResponse create(Long userId, CreateSupportTicketRequest request);

    SupportTicketPageResponse list(Long userId, int page, int size);

    SupportTicketResponse get(Long userId, Long ticketId);

    List<SupportTicketMessageResponse> messages(Long userId, Long ticketId);

    SupportTicketMessageResponse addMessage(
            Long userId, Long ticketId, CreateSupportMessageRequest request);
}
