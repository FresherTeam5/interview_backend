package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.dto.profile.CreateCandidateProfileRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Hồ sơ ứng viên")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    @PostMapping
    @Operation(summary = "Tạo hồ sơ ứng viên thủ công",
            description = "Tạo hồ sơ không cần tải CV; hồ sơ có thể chỉnh sửa và xác nhận như hồ sơ được phân tích từ CV.")
    public CandidateProfileResponse createManual(
            @CurrentUser CustomUserDetails currentUser,
            @Valid @RequestBody CreateCandidateProfileRequest request) {
        return candidateProfileService.createManual(currentUser.getId(), request);
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách hồ sơ ứng viên",
            description = "Trả về các hồ sơ ứng viên thuộc người dùng hiện tại.")
    public List<ProfileSummaryResponse> list(@CurrentUser CustomUserDetails currentUser) {
        return candidateProfileService.list(currentUser.getId());
    }

    @GetMapping("/{profileId}")
    @Operation(
            summary = "Lấy chi tiết hồ sơ ứng viên",
            description = "Trả về đầy đủ thông tin của một hồ sơ ứng viên thuộc người dùng hiện tại.")
    public CandidateProfileResponse get(@CurrentUser CustomUserDetails currentUser,
                                        @PathVariable Long profileId) {
        return candidateProfileService.get(currentUser.getId(), profileId);
    }

    @PutMapping("/{profileId}")
    @Operation(
            summary = "Cập nhật hồ sơ ứng viên",
            description = "Cập nhật thông tin cá nhân, học vấn, kỹ năng và dự án của hồ sơ.")
    public CandidateProfileResponse update(@CurrentUser CustomUserDetails currentUser,
                                           @PathVariable Long profileId,
                                           @Valid @RequestBody ProfileUpdateRequest request) {
        return candidateProfileService.update(currentUser.getId(), profileId, request);
    }

    @PostMapping("/{profileId}/confirm")
    @Operation(
            summary = "Xác nhận hồ sơ ứng viên",
            description = "Đánh dấu hồ sơ đã được kiểm tra và sẵn sàng dùng để tạo phiên phỏng vấn.")
    public CandidateProfileResponse confirm(@CurrentUser CustomUserDetails currentUser,
                                            @PathVariable Long profileId) {
        return candidateProfileService.confirm(currentUser.getId(), profileId);
    }
}
