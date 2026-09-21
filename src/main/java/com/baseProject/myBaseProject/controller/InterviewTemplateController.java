package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.TemplatePageResponse;
import com.baseProject.myBaseProject.dto.template.TemplateVersionRequest;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.CloneInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.TemplateFavoriteResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-templates")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Mẫu phỏng vấn")
public class InterviewTemplateController {
    private final InterviewTemplateService service;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách mẫu phỏng vấn",
            description = "Trả về danh sách phân trang theo phạm vi: mine là mẫu của người dùng, public là mẫu đã công khai.")
    public TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            @CurrentUser CustomUserDetails user,
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String seniority,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String technology,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user.getId(), scope, keyword, seniority,
                language, technology, page, size);
    }

    @PostMapping("/{id}/clone")
    @Operation(summary = "Sao chép mẫu phỏng vấn",
            description = "Tạo một bản nháp riêng từ mẫu mà người dùng có quyền xem.")
    public InterviewTemplateResponse cloneTemplate(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody(required = false) CloneInterviewTemplateRequest request) {
        return service.cloneTemplate(user.getId(), id, request);
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Thêm mẫu vào danh sách yêu thích")
    public TemplateFavoriteResponse favorite(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.favorite(user.getId(), id);
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "Xóa mẫu khỏi danh sách yêu thích")
    public TemplateFavoriteResponse unfavorite(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.unfavorite(user.getId(), id);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy chi tiết mẫu phỏng vấn",
            description = "Trả về nội dung và trạng thái của một mẫu phỏng vấn mà người dùng có quyền truy cập.")
    public InterviewTemplateResponse get(@CurrentUser CustomUserDetails user,
                                         @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Cập nhật mẫu phỏng vấn",
            description = "Chỉnh sửa nội dung của mẫu phỏng vấn chưa được xác nhận.")
    public InterviewTemplateResponse update(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody UpdateInterviewTemplateRequest request) {
        return service.update(user.getId(), id, request);
    }

    @PostMapping("/{id}/confirm")
    @Operation(
            summary = "Xác nhận mẫu phỏng vấn",
            description = "Xác nhận và khóa nội dung mẫu; expectedVersion dùng để ngăn cập nhật đè lên phiên bản mới hơn.")
    public InterviewTemplateResponse confirm(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.confirm(user.getId(), id, request.expectedVersion());
    }

    @PostMapping("/{id}/submit-review")
    @Operation(summary = "Gửi mẫu đã xác nhận vào hàng đợi kiểm duyệt")
    public InterviewTemplateResponse submitReview(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.submitForReview(user.getId(), id, request.expectedVersion());
    }

    @PostMapping("/{id}/publish")
    @IsAdmin
    @Operation(
            summary = "Công khai mẫu phỏng vấn",
            description = "Công khai mẫu đã xác nhận để người dùng khác có thể xem và sử dụng; chỉ quản trị viên được thực hiện.")
    public InterviewTemplateResponse publish(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.publish(user.getId(), id, request.expectedVersion());
    }

    @PostMapping("/{id}/unpublish")
    @IsAdmin
    @Operation(
            summary = "Hủy công khai mẫu phỏng vấn",
            description = "Gỡ mẫu khỏi danh sách công khai; chỉ quản trị viên được thực hiện.")
    public InterviewTemplateResponse unpublish(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.unpublish(user.getId(), id, request.expectedVersion());
    }

    @PostMapping("/{id}/archive")
    @Operation(
            summary = "Lưu trữ mẫu phỏng vấn",
            description = "Đưa mẫu phỏng vấn vào trạng thái lưu trữ và ngừng sử dụng mẫu cho phiên mới.")
    public InterviewTemplateResponse archive(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateVersionRequest request) {
        return service.archive(user.getId(), id, request.expectedVersion());
    }
}
