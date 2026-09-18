package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.feedback.InterviewFeedbackResponse;
import com.baseProject.myBaseProject.dto.feedback.UpsertInterviewFeedbackRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.InterviewFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-sessions/{sessionId}/feedback")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Phản hồi phỏng vấn")
public class InterviewFeedbackController {
    private final InterviewFeedbackService service;

    @GetMapping
    @Operation(summary = "Lấy đánh giá của phiên phỏng vấn")
    public InterviewFeedbackResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long sessionId) {
        return service.get(user.getId(), sessionId);
    }

    @PutMapping
    @Operation(summary = "Tạo hoặc cập nhật đánh giá phiên phỏng vấn")
    public InterviewFeedbackResponse upsert(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long sessionId,
            @Valid @RequestBody UpsertInterviewFeedbackRequest request) {
        return service.upsert(user.getId(), sessionId, request);
    }
}
