package com.baseProject.myBaseProject.dto.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateCandidateProfileRequest(
        @NotBlank(message = "Profile name is required")
        @Size(max = 150, message = "Profile name must not exceed 150 characters")
        String name,
        @Size(max = 255, message = "Headline must not exceed 255 characters")
        String headline,
        @Size(max = 5000, message = "Summary must not exceed 5000 characters")
        String summary,
        @DecimalMin(value = "0.0", message = "Years of experience must not be negative")
        @Digits(integer = 2, fraction = 1,
                message = "Years of experience must not exceed 99.9 and one decimal place")
        BigDecimal yearsExperience,
        @Size(max = 150, message = "Target position must not exceed 150 characters")
        String targetPosition,
        @Size(max = 30, message = "Seniority level must not exceed 30 characters")
        String seniorityLevel,
        @NotNull(message = "Educations are required")
        @Size(max = 20, message = "A profile can contain at most 20 educations")
        List<@Valid ProfileEducationDto> educations,
        @NotNull(message = "Skills are required")
        @Size(max = 100, message = "A profile can contain at most 100 skills")
        List<@Valid ProfileSkillDto> skills,
        @NotNull(message = "Projects are required")
        @Size(max = 50, message = "A profile can contain at most 50 projects")
        List<@Valid ProfileProjectDto> projects) {
}
