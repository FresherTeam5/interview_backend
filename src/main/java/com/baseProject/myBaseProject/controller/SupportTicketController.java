package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.support.CreateSupportTicketRequest;
import com.baseProject.myBaseProject.dto.support.CreateSupportMessageRequest;
import com.baseProject.myBaseProject.dto.support.SupportTicketPageResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketResponse;
import com.baseProject.myBaseProject.dto.support.SupportTicketMessageResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/support-tickets")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Hỗ trợ người dùng")
public class SupportTicketController {
    private final SupportTicketService service;

    @PostMapping
    @Operation(summary = "Tạo yêu cầu hỗ trợ")
    public SupportTicketResponse create(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody CreateSupportTicketRequest request) {
        return service.create(user.getId(), request);
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách yêu cầu hỗ trợ của tôi")
    public SupportTicketPageResponse list(
            @CurrentUser CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user.getId(), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết yêu cầu hỗ trợ")
    public SupportTicketResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Lấy trao đổi công khai của yêu cầu hỗ trợ")
    public List<SupportTicketMessageResponse> messages(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.messages(user.getId(), id);
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Gửi thêm thông tin cho yêu cầu hỗ trợ")
    public SupportTicketMessageResponse addMessage(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody CreateSupportMessageRequest request) {
        return service.addMessage(user.getId(), id, request);
    }
}
