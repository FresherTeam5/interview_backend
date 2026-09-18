package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.dto.profile.CreateCandidateProfileRequest;
import com.baseProject.myBaseProject.entity.CvDocument;

import java.time.Instant;
import java.util.List;

public interface CandidateProfileService {

    void createFromParse(CvDocument document, CvExtractionResult extraction, Instant createdAt);

    CandidateProfileResponse createManual(Long userId, CreateCandidateProfileRequest request);

    List<ProfileSummaryResponse> list(Long userId);

    CandidateProfileResponse get(Long userId, Long profileId);

    CandidateProfileResponse update(Long userId, Long profileId, ProfileUpdateRequest request);

    CandidateProfileResponse confirm(Long userId, Long profileId);
}
