package com.baseProject.myBaseProject.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.baseProject.myBaseProject.dto.auth.CurrentUserResponse;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void anonymousUserCannotReadCurrentProfile() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(authService);
    }

    @Test
    void authenticatedUserCanReadCurrentProfile() throws Exception {
        Instant createdAt = Instant.parse("2026-08-19T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-19T02:00:00Z");
        CurrentUserResponse response = new CurrentUserResponse(
                10L,
                "Nguyen Van A",
                "user@example.com",
                "https://example.com/avatar.png",
                UserRole.USER,
                createdAt,
                null,
                createdAt,
                updatedAt
        );
        when(authService.currentUser(10L)).thenReturn(response);

        mockMvc.perform(get("/api/auth/me").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fullName").value("Nguyen Van A"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-19T01:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-19T02:00:00Z"));

        verify(authService).currentUser(10L);
    }

    private static CustomUserDetails userDetails() {
        return new CustomUserDetails(UserAccount.builder()
                .id(10L)
                .email("user@example.com")
                .passwordHash("password")
                .role(UserRole.USER)
                .enabled(true)
                .build());
    }
}
