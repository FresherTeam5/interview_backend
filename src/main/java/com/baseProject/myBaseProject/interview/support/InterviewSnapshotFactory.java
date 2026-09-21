package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.CandidateProfileSnapshot;
import com.baseProject.myBaseProject.interview.model.InterviewTemplateSnapshot;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.mapper.ProfileMapper;
import com.baseProject.myBaseProject.repository.JobDescriptionAnalysisResultRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class InterviewSnapshotFactory {
    public static final String SNAPSHOT_SCHEMA_VERSION = "v1";

    private final JobDescriptionAnalysisResultRepository jobAnalysisResults;
    private final ProfileEducationRepository educations;
    private final ProfileSkillRepository skills;
    private final ProfileProjectRepository projects;
    private final JobAnalysisJsonMapper jobAnalysisJsonMapper;
    private final ProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    public SnapshotBundle create(InterviewTemplate template, CandidateProfile profile) {
        String jobDescriptionText = jobDescriptionText(template);

        // Lưu cả version và nội dung để ngữ cảnh phỏng vấn không đổi khi template được chỉnh sửa.
        InterviewTemplateSnapshot templateSnapshot = new InterviewTemplateSnapshot(
                SNAPSHOT_SCHEMA_VERSION,
                template.getId(), template.getVersion(), template.getTitle(),
                template.getJobTitle(), template.getTargetSeniority(),
                template.getContentSchemaVersion(),
                jobAnalysisJsonMapper.fromJson(template.getContentJson()),
                jobDescriptionText);

        Long profileId = profile.getId();

        // Đọc các danh sách theo displayOrder để thứ tự trong JSON snapshot luôn ổn định.
        CandidateProfileSnapshot profileSnapshot = new CandidateProfileSnapshot(
                SNAPSHOT_SCHEMA_VERSION,
                profileId, profile.getVersion(), profile.getName(), profile.getHeadline(),
                profile.getSummary(), profile.getYearsExperience(), profile.getTargetPosition(),
                profile.getSeniorityLevel(),
                educations.findByProfileIdOrderByDisplayOrderAsc(profileId).stream()
                        .map(profileMapper::toDto).toList(),
                skills.findByProfileIdOrderByDisplayOrderAsc(profileId).stream()
                        .map(profileMapper::toDto).toList(),
                projects.findByProfileIdOrderByDisplayOrderAsc(profileId).stream()
                        .map(profileMapper::toDto).toList());

        return new SnapshotBundle(
                objectMapper.writeValueAsString(templateSnapshot),
                objectMapper.writeValueAsString(profileSnapshot));
    }

    private String jobDescriptionText(InterviewTemplate template) {
        if (template.getSourceJobDescription() == null) {
            // Template clone giữ analysis trong contentJson nhưng không tham chiếu JD của owner cũ.
            return null;
        }
        return jobAnalysisResults
                .findByJobDescriptionId(template.getSourceJobDescription().getId())
                .orElseThrow(() -> new DomainException(ErrorCode.JD_ANALYSIS_NOT_READY))
                .getExtractedText();
    }

    public record SnapshotBundle(String templateJson, String profileJson) {
    }
}
