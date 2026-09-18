package com.baseProject.myBaseProject.mapper;

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
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.ProfileSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ProfileMapper {

    private static final int MAX_EDUCATIONS = 20;
    private static final int MAX_SKILLS = 100;
    private static final int MAX_PROJECTS = 50;

    private static final short MIN_YEAR = 1900;
    private static final short MAX_YEAR = 2100;
    private static final BigDecimal MAX_YEARS_EXPERIENCE = new BigDecimal("99.9");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern ISO_YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern YEAR_ONLY = Pattern.compile("\\d{4}");
    private static final Pattern MONTH_SLASH_YEAR = Pattern.compile("(\\d{1,2})/(\\d{4})");

    public CandidateProfile newProfile(CvDocument document,
                                       CvExtractionResult result,
                                       Instant createdAt) {
        // Dữ liệu AI không đáng tin hoàn toàn nên cắt theo đúng giới hạn cột trước khi lưu.
        return CandidateProfile.builder()
                .user(document.getUser())
                .cvDocument(document)
                .name(defaultProfileName(document, result))
                .headline(clamp(result.headline(), 255))
                .summary(clamp(result.summary(), 5000))
                .yearsExperience(clampYearsExperience(result.yearsExperience()))
                .targetPosition(clamp(result.targetPosition(), 150))
                .seniorityLevel(clamp(result.seniorityLevel(), 30))
                .source(ProfileSource.AUTO_PARSED)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    public CandidateProfile newManualProfile(UserAccount user,
                                             CreateCandidateProfileRequest request,
                                             Instant createdAt) {
        return CandidateProfile.builder()
                .user(user)
                .name(request.name().trim())
                .headline(blankToNull(request.headline()))
                .summary(blankToNull(request.summary()))
                .yearsExperience(request.yearsExperience())
                .targetPosition(blankToNull(request.targetPosition()))
                .seniorityLevel(blankToNull(request.seniorityLevel()))
                .source(ProfileSource.MANUAL)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    public List<ProfileEducation> newEducations(CandidateProfile profile,
                                                CvExtractionResult result) {
        List<ProfileEducation> educations = new ArrayList<>();
        for (CvExtractionResult.EducationItem item : present(result.educations())) {
            if (educations.size() >= MAX_EDUCATIONS) {
                break;
            }

            String school = clamp(item.school(), 255);
            if (school == null) {
                continue;
            }

            Short startYear = clampYear(item.startYear());
            Short endYear = clampYear(item.endYear());
            if (startYear != null && endYear != null && endYear < startYear) {
                endYear = null;
            }

            educations.add(ProfileEducation.builder()
                    .profile(profile)
                    .school(school)
                    .degree(clamp(item.degree(), 150))
                    .fieldOfStudy(clamp(item.fieldOfStudy(), 150))
                    .startYear(startYear)
                    .endYear(endYear)
                    .userEdited(false)
                    .displayOrder((short) educations.size())
                    .build());
        }
        return educations;
    }

    public List<ProfileSkill> newSkills(CandidateProfile profile, CvExtractionResult result) {
        List<ProfileSkill> skills = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (CvExtractionResult.SkillItem item : present(result.skills())) {
            if (skills.size() >= MAX_SKILLS) {
                break;
            }

            String name = clamp(normalizeSkillName(item.name()), 80);
            // Collation DB không phân biệt hoa thường nên loại trùng trước khi INSERT.
            if (name == null || !seenNames.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }

            skills.add(ProfileSkill.builder()
                    .profile(profile)
                    .name(name)
                    .category(clamp(item.category(), 50))
                    .userEdited(false)
                    .displayOrder((short) skills.size())
                    .build());
        }
        return skills;
    }

    public List<ProfileProject> newProjects(CandidateProfile profile, CvExtractionResult result) {
        List<ProfileProject> projects = new ArrayList<>();
        for (CvExtractionResult.ProjectItem item : present(result.projects())) {
            if (projects.size() >= MAX_PROJECTS) {
                break;
            }

            String name = clamp(item.name(), 255);
            if (name == null) {
                continue;
            }

            LocalDate startDate = parseDate(item.startDate());
            LocalDate endDate = parseDate(item.endDate());
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                endDate = null;
            }

            projects.add(ProfileProject.builder()
                    .profile(profile)
                    .name(name)
                    .description(clamp(item.description(), 5000))
                    .roleInProject(clamp(item.roleInProject(), 150))
                    .techStack(clamp(item.techStack(), 500))
                    .startDate(startDate)
                    .endDate(endDate)
                    .userEdited(false)
                    .displayOrder((short) projects.size())
                    .build());
        }
        return projects;
    }

    public CandidateProfileResponse toResponse(CandidateProfile profile,
                                               List<ProfileEducation> educations,
                                               List<ProfileSkill> skills,
                                               List<ProfileProject> projects) {
        return new CandidateProfileResponse(
                profile.getId(),
                profile.getVersion(),
                displayName(profile),
                profile.getCvDocument() == null ? null : profile.getCvDocument().getId(),
                profile.getCvDocument() == null ? null : profile.getCvDocument().getOriginalFilename(),
                profile.getHeadline(),
                profile.getSummary(),
                profile.getYearsExperience(),
                profile.getTargetPosition(),
                profile.getSeniorityLevel(),
                profile.getSource(),
                profile.getConfirmedAt(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                educations.stream().map(this::toDto).toList(),
                skills.stream().map(this::toDto).toList(),
                projects.stream().map(this::toDto).toList());
    }

    public ProfileSummaryResponse toSummary(CandidateProfile profile,
                                            int educationCount,
                                            int skillCount,
                                            int projectCount) {
        return new ProfileSummaryResponse(
                profile.getId(),
                profile.getVersion(),
                displayName(profile),
                profile.getCvDocument() == null ? null : profile.getCvDocument().getId(),
                profile.getCvDocument() == null ? null : profile.getCvDocument().getOriginalFilename(),
                profile.getHeadline(),
                profile.getTargetPosition(),
                profile.getSeniorityLevel(),
                profile.getSource(),
                profile.getConfirmedAt(),
                educationCount,
                skillCount,
                projectCount,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public ProfileEducationDto toDto(ProfileEducation entity) {
        return new ProfileEducationDto(
                entity.getId(),
                entity.getSchool(),
                entity.getDegree(),
                entity.getFieldOfStudy(),
                entity.getStartYear(),
                entity.getEndYear(),
                entity.isUserEdited(),
                entity.getDisplayOrder());
    }

    public ProfileSkillDto toDto(ProfileSkill entity) {
        return new ProfileSkillDto(
                entity.getId(),
                entity.getName(),
                entity.getCategory(),
                entity.isUserEdited(),
                entity.getDisplayOrder());
    }

    public ProfileProjectDto toDto(ProfileProject entity) {
        return new ProfileProjectDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRoleInProject(),
                entity.getTechStack(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.isUserEdited(),
                entity.getDisplayOrder());
    }

    public void applyScalars(CandidateProfile profile,
                             ProfileUpdateRequest request,
                             Instant updatedAt) {
        profile.setName(request.name().trim());
        profile.setHeadline(blankToNull(request.headline()));
        profile.setSummary(blankToNull(request.summary()));
        profile.setYearsExperience(request.yearsExperience());
        profile.setTargetPosition(blankToNull(request.targetPosition()));
        profile.setSeniorityLevel(blankToNull(request.seniorityLevel()));
        if (profile.getSource() != ProfileSource.MANUAL) {
            profile.setSource(ProfileSource.USER_EDITED);
        }
        profile.setUpdatedAt(updatedAt);
    }

    public ProfileEducation newEducation(CandidateProfile profile,
                                         ProfileEducationDto dto,
                                         short displayOrder) {
        return ProfileEducation.builder()
                .profile(profile)
                .school(dto.school().trim())
                .degree(blankToNull(dto.degree()))
                .fieldOfStudy(blankToNull(dto.fieldOfStudy()))
                .startYear(dto.startYear())
                .endYear(dto.endYear())
                .userEdited(true)
                .displayOrder(displayOrder)
                .build();
    }

    public ProfileSkill newSkill(CandidateProfile profile,
                                 ProfileSkillDto dto,
                                 short displayOrder) {
        return ProfileSkill.builder()
                .profile(profile)
                .name(normalizeSkillName(dto.name()))
                .category(blankToNull(dto.category()))
                .userEdited(true)
                .displayOrder(displayOrder)
                .build();
    }

    public ProfileProject newProject(CandidateProfile profile,
                                     ProfileProjectDto dto,
                                     short displayOrder) {
        return ProfileProject.builder()
                .profile(profile)
                .name(dto.name().trim())
                .description(blankToNull(dto.description()))
                .roleInProject(blankToNull(dto.roleInProject()))
                .techStack(blankToNull(dto.techStack()))
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .userEdited(true)
                .displayOrder(displayOrder)
                .build();
    }

    public void apply(ProfileEducation entity, ProfileEducationDto dto, short displayOrder) {
        String school = dto.school().trim();
        String degree = blankToNull(dto.degree());
        String fieldOfStudy = blankToNull(dto.fieldOfStudy());
        if (!Objects.equals(entity.getSchool(), school)
                || !Objects.equals(entity.getDegree(), degree)
                || !Objects.equals(entity.getFieldOfStudy(), fieldOfStudy)
                || !Objects.equals(entity.getStartYear(), dto.startYear())
                || !Objects.equals(entity.getEndYear(), dto.endYear())) {
            entity.setUserEdited(true);
        }
        entity.setSchool(school);
        entity.setDegree(degree);
        entity.setFieldOfStudy(fieldOfStudy);
        entity.setStartYear(dto.startYear());
        entity.setEndYear(dto.endYear());
        entity.setDisplayOrder(displayOrder);
    }

    public void apply(ProfileSkill entity, ProfileSkillDto dto, short displayOrder) {
        String name = normalizeSkillName(dto.name());
        String category = blankToNull(dto.category());
        if (!Objects.equals(entity.getName(), name)
                || !Objects.equals(entity.getCategory(), category)) {
            entity.setUserEdited(true);
        }
        entity.setName(name);
        entity.setCategory(category);
        entity.setDisplayOrder(displayOrder);
    }

    public void apply(ProfileProject entity, ProfileProjectDto dto, short displayOrder) {
        String name = dto.name().trim();
        String description = blankToNull(dto.description());
        String roleInProject = blankToNull(dto.roleInProject());
        String techStack = blankToNull(dto.techStack());
        if (!Objects.equals(entity.getName(), name)
                || !Objects.equals(entity.getDescription(), description)
                || !Objects.equals(entity.getRoleInProject(), roleInProject)
                || !Objects.equals(entity.getTechStack(), techStack)
                || !Objects.equals(entity.getStartDate(), dto.startDate())
                || !Objects.equals(entity.getEndDate(), dto.endDate())) {
            entity.setUserEdited(true);
        }
        entity.setName(name);
        entity.setDescription(description);
        entity.setRoleInProject(roleInProject);
        entity.setTechStack(techStack);
        entity.setStartDate(dto.startDate());
        entity.setEndDate(dto.endDate());
        entity.setDisplayOrder(displayOrder);
    }

    public static String normalizeSkillName(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : WHITESPACE.matcher(normalized).replaceAll(" ");
    }

    private String defaultProfileName(CvDocument document, CvExtractionResult result) {
        String name = blankToNull(result.targetPosition());
        if (name == null) {
            name = blankToNull(result.headline());
        }
        if (name == null) {
            name = withoutPdfExtension(document.getOriginalFilename());
        }
        return clamp(name == null ? "Candidate profile" : name, 150);
    }

    private String displayName(CandidateProfile profile) {
        String name = blankToNull(profile.getName());
        if (name == null) {
            name = blankToNull(profile.getTargetPosition());
        }
        if (name == null) {
            name = blankToNull(profile.getHeadline());
        }
        if (name == null) {
            name = profile.getCvDocument() == null
                    ? null : withoutPdfExtension(profile.getCvDocument().getOriginalFilename());
        }
        return name == null ? "Candidate profile" : name;
    }

    private String withoutPdfExtension(String filename) {
        String value = blankToNull(filename);
        if (value != null && value.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            value = blankToNull(value.substring(0, value.length() - 4));
        }
        return value;
    }

    private BigDecimal clampYearsExperience(BigDecimal yearsExperience) {
        if (yearsExperience == null) {
            return null;
        }
        return yearsExperience.max(BigDecimal.ZERO)
                .min(MAX_YEARS_EXPERIENCE)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Short clampYear(Short year) {
        return year == null || year < MIN_YEAR || year > MAX_YEAR ? null : year;
    }

    private LocalDate parseDate(String rawValue) {
        String value = blankToNull(rawValue);
        if (value == null) {
            return null;
        }

        try {
            LocalDate date = null;
            if (ISO_DATE.matcher(value).matches()) {
                date = LocalDate.parse(value);
            } else if (ISO_YEAR_MONTH.matcher(value).matches()) {
                date = LocalDate.parse(value + "-01");
            } else if (YEAR_ONLY.matcher(value).matches()) {
                date = LocalDate.of(Integer.parseInt(value), 1, 1);
            } else {
                Matcher matcher = MONTH_SLASH_YEAR.matcher(value);
                if (matcher.matches()) {
                    date = LocalDate.of(
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(1)),
                            1);
                }
            }

            return date == null || date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR
                    ? null
                    : date;
        } catch (DateTimeException e) {
            log.debug("Ignoring invalid date returned by CV parser: {}", value);
            return null;
        }
    }

    private static String clamp(String rawValue, int maxLength) {
        String value = blankToNull(rawValue);
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return blankToNull(value.substring(0, maxLength));
    }

    private static String blankToNull(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        return value.isEmpty() ? null : value;
    }

    private <T> List<T> present(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }
}
