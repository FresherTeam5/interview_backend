package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.dto.profile.ProfileSkillDto;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.JobDescriptionAnalysisResult;
import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.JobDescriptionAnalysisResultRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewSnapshotFactoryTest {
    @Test
    void snapshotContainsConfirmedValuesAndDoesNotFollowLaterProfileChanges() {
        JobDescriptionAnalysisResultRepository analyses =
                mock(JobDescriptionAnalysisResultRepository.class);
        ProfileEducationRepository educations = mock(ProfileEducationRepository.class);
        ProfileSkillRepository skills = mock(ProfileSkillRepository.class);
        ProfileProjectRepository projects = mock(ProfileProjectRepository.class);
        JobAnalysisJsonMapper jobMapper = mock(JobAnalysisJsonMapper.class);
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        JobDescriptionDocument document = JobDescriptionDocument.builder().id(10L).build();
        InterviewTemplate template = new InterviewTemplate();
        template.setId(101L);
        template.setTitle("Backend Java");
        template.setSourceJobDescription(document);
        template.setContentJson("{}");
        template.setContentSchemaVersion("v2");
        CandidateProfile profile = CandidateProfile.builder()
                .id(35L)
                .name("Minh profile")
                .headline("Java Developer")
                .build();
        ProfileSkill skill = ProfileSkill.builder().id(8L).name("Spring Boot").build();
        JobAnalysis jobAnalysis = new JobAnalysis(
                true, "vi", "Backend", "Junior", "IT", "Backend role", List.of());

        when(analyses.findByJobDescriptionId(10L)).thenReturn(Optional.of(
                JobDescriptionAnalysisResult.builder()
                        .jobDescription(document)
                        .extractedText("Build Java APIs")
                        .analysisJson("{}")
                        .schemaVersion("v2")
                        .modelName("test")
                        .build()));
        when(jobMapper.fromJson("{}")).thenReturn(jobAnalysis);
        when(educations.findByProfileIdOrderByDisplayOrderAsc(35L)).thenReturn(List.of());
        when(skills.findByProfileIdOrderByDisplayOrderAsc(35L)).thenReturn(List.of(skill));
        when(projects.findByProfileIdOrderByDisplayOrderAsc(35L)).thenReturn(List.of());
        when(profileMapper.toDto(skill)).thenReturn(new ProfileSkillDto(
                8L, "Spring Boot", "FRAMEWORK", false, (short) 0));

        InterviewSnapshotFactory factory = new InterviewSnapshotFactory(
                analyses, educations, skills, projects, jobMapper, profileMapper, objectMapper);
        var snapshots = factory.create(template, profile);
        profile.setName("Changed after session creation");

        var templateJson = objectMapper.readTree(snapshots.templateJson());
        var profileJson = objectMapper.readTree(snapshots.profileJson());
        assertThat(templateJson.get("snapshotSchemaVersion").asText()).isEqualTo("v1");
        assertThat(templateJson.get("jobDescriptionText").asText()).isEqualTo("Build Java APIs");
        assertThat(profileJson.get("name").asText()).isEqualTo("Minh profile");
        assertThat(profileJson.get("skills").get(0).get("name").asText())
                .isEqualTo("Spring Boot");
    }

    @Test
    void clonedTemplateSnapshotDoesNotRequireSourceJobDescription() {
        JobDescriptionAnalysisResultRepository analyses =
                mock(JobDescriptionAnalysisResultRepository.class);
        ProfileEducationRepository educations = mock(ProfileEducationRepository.class);
        ProfileSkillRepository skills = mock(ProfileSkillRepository.class);
        ProfileProjectRepository projects = mock(ProfileProjectRepository.class);
        JobAnalysisJsonMapper jobMapper = mock(JobAnalysisJsonMapper.class);
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        InterviewTemplate template = new InterviewTemplate();
        template.setId(102L);
        template.setTitle("Cloned Backend Java");
        template.setContentJson("{}");
        template.setContentSchemaVersion("v2");
        CandidateProfile profile = CandidateProfile.builder()
                .id(36L)
                .name("Admin profile")
                .build();
        JobAnalysis jobAnalysis = new JobAnalysis(
                true, "vi", "Backend", "Junior", "IT", "Backend role", List.of());

        when(jobMapper.fromJson("{}")).thenReturn(jobAnalysis);
        when(educations.findByProfileIdOrderByDisplayOrderAsc(36L)).thenReturn(List.of());
        when(skills.findByProfileIdOrderByDisplayOrderAsc(36L)).thenReturn(List.of());
        when(projects.findByProfileIdOrderByDisplayOrderAsc(36L)).thenReturn(List.of());

        InterviewSnapshotFactory factory = new InterviewSnapshotFactory(
                analyses, educations, skills, projects, jobMapper, profileMapper, objectMapper);

        var snapshots = factory.create(template, profile);
        var templateJson = objectMapper.readTree(snapshots.templateJson());

        assertThat(templateJson.get("analysis").get("jobTitle").asText())
                .isEqualTo("Backend");
        assertThat(templateJson.get("jobDescriptionText").isNull()).isTrue();
        verifyNoInteractions(analyses);
    }
}
