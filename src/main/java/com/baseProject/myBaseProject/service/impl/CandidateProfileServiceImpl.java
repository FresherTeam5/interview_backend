package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileEducationDto;
import com.baseProject.myBaseProject.dto.profile.ProfileProjectDto;
import com.baseProject.myBaseProject.dto.profile.ProfileSkillDto;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.dto.profile.CreateCandidateProfileRequest;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.ProfileEducation;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.projection.ProfileItemCount;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class CandidateProfileServiceImpl implements CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final ProfileEducationRepository educationRepository;
    private final ProfileSkillRepository skillRepository;
    private final ProfileProjectRepository projectRepository;
    private final ProfileMapper profileMapper;
    private final UserAccountRepository users;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public void createFromParse(CvDocument document,
                                CvExtractionResult extraction,
                                Instant createdAt) {
        if (candidateProfileRepository.existsByCvDocumentId(document.getId())) {
            throw new IllegalStateException(
                    "CV document id=%d already has a candidate profile".formatted(document.getId()));
        }

        CandidateProfile profile = candidateProfileRepository.save(
                profileMapper.newProfile(document, extraction, createdAt));
        educationRepository.saveAll(profileMapper.newEducations(profile, extraction));
        skillRepository.saveAll(profileMapper.newSkills(profile, extraction));
        projectRepository.saveAll(profileMapper.newProjects(profile, extraction));
    }

    @Override
    @Transactional
    public CandidateProfileResponse createManual(
            Long userId, CreateCandidateProfileRequest request) {
        rejectDuplicateSkillNames(request.skills());
        Instant now = clock.instant();
        CandidateProfile profile = candidateProfileRepository.save(
                profileMapper.newManualProfile(users.getReferenceById(userId), request, now));

        List<ProfileEducation> educations = new ArrayList<>();
        for (int index = 0; index < request.educations().size(); index++) {
            educations.add(profileMapper.newEducation(
                    profile, request.educations().get(index), (short) index));
        }
        List<ProfileSkill> skills = new ArrayList<>();
        for (int index = 0; index < request.skills().size(); index++) {
            skills.add(profileMapper.newSkill(profile, request.skills().get(index), (short) index));
        }
        List<ProfileProject> projects = new ArrayList<>();
        for (int index = 0; index < request.projects().size(); index++) {
            projects.add(profileMapper.newProject(
                    profile, request.projects().get(index), (short) index));
        }
        educationRepository.saveAll(educations);
        skillRepository.saveAll(skills);
        projectRepository.saveAll(projects);
        entityManager.flush();
        return toResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSummaryResponse> list(Long userId) {
        List<CandidateProfile> profiles = candidateProfileRepository
                .findAvailableByUserId(userId);
        if (profiles.isEmpty()) {
            return List.of();
        }

        List<Long> profileIds = profiles.stream().map(CandidateProfile::getId).toList();
        Map<Long, Integer> educationCounts = countMap(
                educationRepository.countGroupedByProfileIds(profileIds));
        Map<Long, Integer> skillCounts = countMap(
                skillRepository.countGroupedByProfileIds(profileIds));
        Map<Long, Integer> projectCounts = countMap(
                projectRepository.countGroupedByProfileIds(profileIds));

        return profiles.stream()
                .map(profile -> profileMapper.toSummary(
                        profile,
                        educationCounts.getOrDefault(profile.getId(), 0),
                        skillCounts.getOrDefault(profile.getId(), 0),
                        projectCounts.getOrDefault(profile.getId(), 0)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateProfileResponse get(Long userId, Long profileId) {
        return toResponse(requireProfile(userId, profileId));
    }

    @Override
    @Transactional
    public CandidateProfileResponse update(Long userId,
                                           Long profileId,
                                           ProfileUpdateRequest request) {
        CandidateProfile profile = requireProfileForUpdate(userId, profileId);
        if (profile.getVersion() != request.version()) {
            throw new DomainException(ErrorCode.PROFILE_VERSION_CONFLICT);
        }

        rejectDuplicateSkillNames(request.skills());

        ChildPlan<ProfileEducation> educations = planChildren(
                educationRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                request.educations(),
                ProfileEducation::getId,
                ProfileEducationDto::id,
                "education");
        ChildPlan<ProfileSkill> skills = planChildren(
                skillRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                request.skills(),
                ProfileSkill::getId,
                ProfileSkillDto::id,
                "skill");
        ChildPlan<ProfileProject> projects = planChildren(
                projectRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                request.projects(),
                ProfileProject::getId,
                ProfileProjectDto::id,
                "project");

        profileMapper.applyScalars(profile, request, clock.instant());

        educationRepository.deleteAll(educations.removals());
        skillRepository.deleteAll(skills.removals());
        projectRepository.deleteAll(projects.removals());
        entityManager.flush();

        List<ProfileEducation> newEducations = new ArrayList<>();
        for (int index = 0; index < request.educations().size(); index++) {
            ProfileEducationDto item = request.educations().get(index);
            if (item.id() == null) {
                newEducations.add(profileMapper.newEducation(profile, item, (short) index));
            } else {
                profileMapper.apply(educations.byId(item.id()), item, (short) index);
            }
        }

        List<ProfileSkill> newSkills = new ArrayList<>();
        for (int index = 0; index < request.skills().size(); index++) {
            ProfileSkillDto item = request.skills().get(index);
            if (item.id() == null) {
                newSkills.add(profileMapper.newSkill(profile, item, (short) index));
            } else {
                profileMapper.apply(skills.byId(item.id()), item, (short) index);
            }
        }

        List<ProfileProject> newProjects = new ArrayList<>();
        for (int index = 0; index < request.projects().size(); index++) {
            ProfileProjectDto item = request.projects().get(index);
            if (item.id() == null) {
                newProjects.add(profileMapper.newProject(profile, item, (short) index));
            } else {
                profileMapper.apply(projects.byId(item.id()), item, (short) index);
            }
        }

        educationRepository.saveAll(newEducations);
        skillRepository.saveAll(newSkills);
        projectRepository.saveAll(newProjects);
        entityManager.flush();

        return toResponse(profile);
    }

    @Override
    @Transactional
    public CandidateProfileResponse confirm(Long userId, Long profileId) {
        CandidateProfile profile = requireProfileForUpdate(userId, profileId);
        if (!profile.isConfirmed()) {
            Instant now = clock.instant();
            profile.setConfirmedAt(now);
            profile.setUpdatedAt(now);
            entityManager.flush();
        }
        return toResponse(profile);
    }

    private CandidateProfile requireProfile(Long userId, Long profileId) {
        return candidateProfileRepository
                .findAvailableByIdAndUserId(profileId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private CandidateProfile requireProfileForUpdate(Long userId, Long profileId) {
        return candidateProfileRepository
                .findActiveOwnedByIdForUpdate(profileId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.PROFILE_NOT_FOUND));
    }

    private CandidateProfileResponse toResponse(CandidateProfile profile) {
        Long profileId = profile.getId();
        return profileMapper.toResponse(
                profile,
                educationRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                skillRepository.findByProfileIdOrderByDisplayOrderAsc(profileId),
                projectRepository.findByProfileIdOrderByDisplayOrderAsc(profileId));
    }

    private void rejectDuplicateSkillNames(List<ProfileSkillDto> skills) {
        Set<String> seenNames = new HashSet<>();
        for (ProfileSkillDto skill : skills) {
            String normalized = ProfileMapper.normalizeSkillName(skill.name());
            if (!seenNames.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new DomainException(
                        ErrorCode.DUPLICATE_SKILL_NAME,
                        "Duplicate skill name: %s".formatted(normalized));
            }
        }
    }

    private <E, D> ChildPlan<E> planChildren(List<E> existing,
                                             List<D> requested,
                                             Function<E, Long> entityId,
                                             Function<D, Long> dtoId,
                                             String itemType) {
        Map<Long, E> existingById = new LinkedHashMap<>();
        existing.forEach(entity -> existingById.put(entityId.apply(entity), entity));

        Set<Long> keptIds = new HashSet<>();
        for (D item : requested) {
            Long id = dtoId.apply(item);
            if (id == null) {
                continue;
            }
            if (!existingById.containsKey(id)) {
                throw new DomainException(
                        ErrorCode.PROFILE_ITEM_NOT_FOUND,
                        "Profile %s id=%d was not found".formatted(itemType, id));
            }
            if (!keptIds.add(id)) {
                throw new DomainException(
                        ErrorCode.PROFILE_ITEM_NOT_FOUND,
                        "Profile %s id=%d appears more than once".formatted(itemType, id));
            }
        }

        List<E> removals = existingById.entrySet().stream()
                .filter(entry -> !keptIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        return new ChildPlan<>(existingById, removals);
    }

    private Map<Long, Integer> countMap(List<ProfileItemCount> counts) {
        Map<Long, Integer> result = new HashMap<>();
        counts.forEach(count -> result.put(
                count.getProfileId(), Math.toIntExact(count.getItemCount())));
        return result;
    }

    private record ChildPlan<E>(Map<Long, E> byId, List<E> removals) {
        E byId(Long id) {
            return byId.get(id);
        }
    }
}
