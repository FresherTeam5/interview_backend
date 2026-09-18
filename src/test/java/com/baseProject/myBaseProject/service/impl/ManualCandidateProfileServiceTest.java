package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.profile.CreateCandidateProfileRequest;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.ProfileSource;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualCandidateProfileServiceTest {
    @Test
    void createsProfileWithoutCvAndReturnsNullableCvFields() {
        Instant now = Instant.parse("2026-09-18T03:00:00Z");
        CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
        ProfileEducationRepository educations = mock(ProfileEducationRepository.class);
        ProfileSkillRepository skills = mock(ProfileSkillRepository.class);
        ProfileProjectRepository projects = mock(ProfileProjectRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        when(users.getReferenceById(7L)).thenReturn(UserAccount.builder().id(7L).build());
        when(profiles.save(any())).thenAnswer(invocation -> {
            CandidateProfile profile = invocation.getArgument(0);
            profile.setId(22L);
            return profile;
        });
        when(educations.findByProfileIdOrderByDisplayOrderAsc(22L)).thenReturn(List.of());
        when(skills.findByProfileIdOrderByDisplayOrderAsc(22L)).thenReturn(List.of());
        when(projects.findByProfileIdOrderByDisplayOrderAsc(22L)).thenReturn(List.of());
        CandidateProfileServiceImpl service = new CandidateProfileServiceImpl(
                profiles, educations, skills, projects, new ProfileMapper(), users,
                mock(EntityManager.class), Clock.fixed(now, ZoneOffset.UTC));

        var response = service.createManual(7L, new CreateCandidateProfileRequest(
                "Backend profile", "Java developer", "API experience",
                new BigDecimal("2.5"), "Backend Engineer", "Middle",
                List.of(), List.of(), List.of()));

        assertThat(response.id()).isEqualTo(22L);
        assertThat(response.source()).isEqualTo(ProfileSource.MANUAL);
        assertThat(response.cvDocumentId()).isNull();
        assertThat(response.cvOriginalFilename()).isNull();
        assertThat(response.createdAt()).isEqualTo(now);
    }
}
