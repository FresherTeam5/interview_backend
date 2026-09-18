package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateProfileServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-05T02:00:00Z");

    @Mock
    private CandidateProfileRepository profileRepository;
    @Mock
    private ProfileEducationRepository educationRepository;
    @Mock
    private ProfileSkillRepository skillRepository;
    @Mock
    private ProfileProjectRepository projectRepository;
    @Mock
    private ProfileMapper profileMapper;
    @Mock
    private EntityManager entityManager;

    private CandidateProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CandidateProfileServiceImpl(
                profileRepository,
                educationRepository,
                skillRepository,
                projectRepository,
                profileMapper,
                mock(com.baseProject.myBaseProject.repository.UserAccountRepository.class),
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listLoadsChildCountsInGroupedQueries() {
        CvDocument document = CvDocument.builder().id(11L).originalFilename("cv.pdf").build();
        CandidateProfile profile = CandidateProfile.builder()
                .id(21L)
                .name("Backend profile")
                .cvDocument(document)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        ProfileItemCount educationCount = count(21L, 2L);
        ProfileItemCount skillCount = count(21L, 5L);
        ProfileItemCount projectCount = count(21L, 3L);
        ProfileSummaryResponse expected = new ProfileSummaryResponse(
                21L, 0L, "Backend profile", 11L, "cv.pdf", null, null, null,
                null, null, 2, 5, 3, NOW, NOW);

        when(profileRepository.findAvailableByUserId(4L))
                .thenReturn(List.of(profile));
        when(educationRepository.countGroupedByProfileIds(List.of(21L)))
                .thenReturn(List.of(educationCount));
        when(skillRepository.countGroupedByProfileIds(List.of(21L)))
                .thenReturn(List.of(skillCount));
        when(projectRepository.countGroupedByProfileIds(List.of(21L)))
                .thenReturn(List.of(projectCount));
        when(profileMapper.toSummary(profile, 2, 5, 3)).thenReturn(expected);

        assertThat(service.list(4L)).containsExactly(expected);
        verify(educationRepository).countGroupedByProfileIds(List.of(21L));
        verify(skillRepository).countGroupedByProfileIds(List.of(21L));
        verify(projectRepository).countGroupedByProfileIds(List.of(21L));
    }

    @Test
    void updateRejectsStaleProfileVersionBeforeChangingChildren() {
        CandidateProfile profile = CandidateProfile.builder()
                .id(21L)
                .version(5L)
                .build();
        ProfileUpdateRequest request = new ProfileUpdateRequest(
                4L, "Backend profile", null, null, null, null, null,
                List.of(), List.of(), List.of());
        when(profileRepository.findActiveOwnedByIdForUpdate(21L, 4L))
                .thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.update(4L, 21L, request))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.PROFILE_VERSION_CONFLICT));

        verifyNoInteractions(educationRepository, skillRepository, projectRepository, profileMapper);
    }

    @Test
    void confirmIsIdempotentAndReturnsFullProfile() {
        CandidateProfile profile = CandidateProfile.builder()
                .id(21L)
                .name("Backend profile")
                .build();
        CandidateProfileResponse expected = new CandidateProfileResponse(
                21L, 0L, "Backend profile", 11L, "cv.pdf", null, null, null,
                null, null, null, NOW, NOW, NOW, List.of(), List.of(), List.of());
        when(profileRepository.findActiveOwnedByIdForUpdate(21L, 4L))
                .thenReturn(Optional.of(profile));
        when(educationRepository.findByProfileIdOrderByDisplayOrderAsc(21L))
                .thenReturn(List.of());
        when(skillRepository.findByProfileIdOrderByDisplayOrderAsc(21L))
                .thenReturn(List.of());
        when(projectRepository.findByProfileIdOrderByDisplayOrderAsc(21L))
                .thenReturn(List.of());
        when(profileMapper.toResponse(profile, List.of(), List.of(), List.of()))
                .thenReturn(expected);

        assertThat(service.confirm(4L, 21L)).isSameAs(expected);
        assertThat(profile.getConfirmedAt()).isEqualTo(NOW);
        assertThat(profile.getUpdatedAt()).isEqualTo(NOW);
        verify(entityManager).flush();
    }

    private ProfileItemCount count(Long profileId, long itemCount) {
        ProfileItemCount count = mock(ProfileItemCount.class);
        when(count.getProfileId()).thenReturn(profileId);
        when(count.getItemCount()).thenReturn(itemCount);
        return count;
    }
}
