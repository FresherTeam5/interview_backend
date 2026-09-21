package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.ProfileSource;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CandidateProfileControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-05T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandidateProfileService candidateProfileService;

    @Test
    void anonymousUserCannotListProfiles() throws Exception {
        mockMvc.perform(get("/api/profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(candidateProfileService);
    }

    @Test
    void userCanListOwnProfiles() throws Exception {
        ProfileSummaryResponse response = new ProfileSummaryResponse(
                21L,
                2L,
                "Backend profile",
                11L,
                "backend-cv.pdf",
                "Java Developer",
                "Backend Engineer",
                "JUNIOR",
                ProfileSource.USER_EDITED,
                null,
                1,
                5,
                2,
                CREATED_AT,
                CREATED_AT);
        when(candidateProfileService.list(4L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/profiles").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(21))
                .andExpect(jsonPath("$[0].name").value("Backend profile"))
                .andExpect(jsonPath("$[0].skillCount").value(5));

        verify(candidateProfileService).list(4L);
    }

    @Test
    void adminInheritsUserAccessAndCanListOwnProfiles() throws Exception {
        when(candidateProfileService.list(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/profiles").with(user(userDetails(UserRole.ADMIN, 3L))))
                .andExpect(status().isOk());

        verify(candidateProfileService).list(3L);
    }

    @Test
    void validUpdateDelegatesToProfileService() throws Exception {
        CandidateProfileResponse response = response(null);
        when(candidateProfileService.update(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq(21L),
                any(ProfileUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/profiles/21")
                        .with(user(userDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 2,
                                  "name": "Backend profile",
                                  "headline": "Java Developer",
                                  "summary": "Spring Boot developer",
                                  "yearsExperience": 1.5,
                                  "targetPosition": "Backend Engineer",
                                  "seniorityLevel": "JUNIOR",
                                  "educations": [],
                                  "skills": [],
                                  "projects": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.summary").value("Spring Boot developer"));

        verify(candidateProfileService).update(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq(21L),
                any(ProfileUpdateRequest.class));
    }

    @Test
    void updateRejectsMissingProfileName() throws Exception {
        mockMvc.perform(put("/api/profiles/21")
                        .with(user(userDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 2,
                                  "educations": [],
                                  "skills": [],
                                  "projects": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void userCanConfirmProfile() throws Exception {
        Instant confirmedAt = Instant.parse("2026-09-05T02:00:00Z");
        when(candidateProfileService.confirm(4L, 21L)).thenReturn(response(confirmedAt));

        mockMvc.perform(post("/api/profiles/21/confirm").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedAt").value("2026-09-05T02:00:00Z"));

        verify(candidateProfileService).confirm(4L, 21L);
    }

    private CandidateProfileResponse response(Instant confirmedAt) {
        return new CandidateProfileResponse(
                21L,
                2L,
                "Backend profile",
                11L,
                "backend-cv.pdf",
                "Java Developer",
                "Spring Boot developer",
                null,
                "Backend Engineer",
                "JUNIOR",
                ProfileSource.USER_EDITED,
                confirmedAt,
                CREATED_AT,
                CREATED_AT,
                List.of(),
                List.of(),
                List.of());
    }

    private CustomUserDetails userDetails() {
        return userDetails(UserRole.USER, 4L);
    }

    private CustomUserDetails userDetails(UserRole role, Long id) {
        return new CustomUserDetails(UserAccount.builder()
                .id(id)
                .email(role == UserRole.ADMIN
                        ? "admin@example.com" : "candidate@example.com")
                .passwordHash("password")
                .role(role)
                .enabled(true)
                .build());
    }
}
