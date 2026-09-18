package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.InterviewSessionProperties;
import com.baseProject.myBaseProject.config.properites.RealtimeProperties;
import com.baseProject.myBaseProject.config.properites.SpeechProperties;
import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewReadinessResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.enums.InterviewReadinessCheckStatus;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.service.InterviewReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InterviewReadinessServiceImpl implements InterviewReadinessService {
    private final InterviewTemplateRepository templates;
    private final CandidateProfileRepository profiles;
    private final InterviewSessionProperties sessionProperties;
    private final RealtimeProperties realtimeProperties;
    private final SpeechProperties speechProperties;

    @Override
    @Transactional(readOnly = true)
    public InterviewReadinessResponse check(
            Long userId, CreateInterviewSessionRequest request) {
        List<InterviewReadinessResponse.Check> checks = new ArrayList<>();
        InterviewSessionMode mode = request.mode() == null
                ? InterviewSessionMode.TURN_BASED
                : request.mode();

        checkTemplate(userId, request.templateId(), checks);
        checkProfile(userId, request.profileId(), checks);
        add(checks,
                "LANGUAGE_SUPPORTED",
                sessionProperties.supportedLanguages().contains(
                        request.languageCode().strip().toLowerCase(Locale.ROOT)),
                "Interview language is supported",
                "Interview language is not supported");
        add(checks,
                "DURATION_SUPPORTED",
                sessionProperties.supportedDurations().contains(request.durationMinutes()),
                "Interview duration is supported",
                "Interview duration is not supported");
        add(checks,
                "MODE_SUPPORTED",
                sessionProperties.supportedModes().contains(mode),
                "Interview mode is supported",
                "Interview mode is not supported");

        if (mode == InterviewSessionMode.VOICE_REALTIME) {
            add(checks,
                    "REALTIME_AVAILABLE",
                    realtimeProperties.enabled(),
                    "Realtime interview is available",
                    "Realtime interview is currently unavailable");
        }
        if (!speechProperties.enabled()) {
            checks.add(new InterviewReadinessResponse.Check(
                    "PUSH_TO_TALK_AVAILABLE",
                    InterviewReadinessCheckStatus.WARNING,
                    "Push-to-talk transcription is unavailable; text input remains available"));
        } else {
            checks.add(new InterviewReadinessResponse.Check(
                    "PUSH_TO_TALK_AVAILABLE",
                    InterviewReadinessCheckStatus.PASS,
                    "Push-to-talk transcription is available"));
        }

        boolean ready = checks.stream()
                .noneMatch(check -> check.status() == InterviewReadinessCheckStatus.FAIL);
        return new InterviewReadinessResponse(
                ready,
                mode,
                new InterviewReadinessResponse.Capabilities(
                        true,
                        speechProperties.enabled(),
                        realtimeProperties.enabled(),
                        true),
                List.copyOf(checks));
    }

    private void checkTemplate(
            Long userId,
            Long templateId,
            List<InterviewReadinessResponse.Check> checks) {
        InterviewTemplate template = templates.findAccessibleForSession(templateId, userId)
                .orElse(null);
        add(checks,
                "TEMPLATE_ACCESSIBLE",
                template != null,
                "Interview template is accessible",
                "Interview template was not found or is not accessible");
        if (template != null) {
            add(checks,
                    "TEMPLATE_ACTIVE",
                    template.getArchivedAt() == null,
                    "Interview template is active",
                    "Interview template is archived");
            add(checks,
                    "TEMPLATE_CONFIRMED",
                    template.isConfirmed(),
                    "Interview template is confirmed",
                    "Interview template must be confirmed first");
        }
    }

    private void checkProfile(
            Long userId,
            Long profileId,
            List<InterviewReadinessResponse.Check> checks) {
        CandidateProfile profile = profiles
                .findAvailableByIdAndUserId(profileId, userId)
                .orElse(null);
        add(checks,
                "PROFILE_ACCESSIBLE",
                profile != null,
                "Candidate profile is accessible",
                "Candidate profile was not found or its CV is inactive");
        if (profile != null) {
            add(checks,
                    "PROFILE_CONFIRMED",
                    profile.isConfirmed(),
                    "Candidate profile is confirmed",
                    "Candidate profile must be confirmed first");
        }
    }

    private void add(
            List<InterviewReadinessResponse.Check> checks,
            String code,
            boolean passed,
            String success,
            String failure) {
        checks.add(new InterviewReadinessResponse.Check(
                code,
                passed
                        ? InterviewReadinessCheckStatus.PASS
                        : InterviewReadinessCheckStatus.FAIL,
                passed ? success : failure));
    }
}
