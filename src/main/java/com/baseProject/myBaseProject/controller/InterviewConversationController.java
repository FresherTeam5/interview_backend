package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.session.InterviewAnswerResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.SubmitInterviewAnswerRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-sessions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Hội thoại phỏng vấn")
public class InterviewConversationController {
    private final InterviewConversationService service;

    @PostMapping("/{id}/start")
    @Operation(
            summary = "Bắt đầu phỏng vấn",
            description = "Bắt đầu phiên đã chuẩn bị, khởi chạy bộ đếm thời gian và trả về câu hỏi đầu tiên.")
    public InterviewConversationResponse start(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.start(user.getId(), id);
    }

    @GetMapping("/{id}/conversation")
    @Operation(
            summary = "Lấy hội thoại phỏng vấn",
            description = "Trả về hội thoại đã lưu và trạng thái hiện tại để người dùng tiếp tục phiên phỏng vấn.")
    public InterviewConversationResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PostMapping("/{id}/answers")
    @Operation(
            summary = "Gửi câu trả lời phỏng vấn",
            description = "Lưu câu trả lời của ứng viên và tạo lượt hỏi tiếp theo; hỗ trợ Idempotency-Key để tránh gửi trùng.")
    public InterviewAnswerResponse answer(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitInterviewAnswerRequest request) {
        return service.answer(user.getId(), id, idempotencyKey, request);
    }

    @PostMapping("/{id}/turns/{turnId}/retry")
    @Operation(
            summary = "Thử lại lượt trả lời bị lỗi",
            description = "Dùng lại nội dung và idempotency key của candidate turn FAILED; không tạo câu trả lời trùng.")
    public InterviewAnswerResponse retryFailedAnswer(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @PathVariable Long turnId) {
        return service.retryFailedAnswer(user.getId(), id, turnId);
    }

    @PostMapping("/{id}/finish")
    @Operation(
            summary = "Kết thúc phỏng vấn",
            description = "Kết thúc phiên trước thời hạn và chuyển cuộc phỏng vấn sang bước chấm điểm.")
    public InterviewConversationResponse finish(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.finish(user.getId(), id);
    }
}
