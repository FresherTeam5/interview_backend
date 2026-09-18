package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewReadinessCheckStatus;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;

import java.util.List;

public record InterviewReadinessResponse(
        boolean ready,
        InterviewSessionMode requestedMode,
        Capabilities capabilities,
        List<Check> checks) {

    public record Capabilities(
            boolean textInput,
            boolean pushToTalk,
            boolean realtimeVoice,
            boolean realtimeFallbackToTurnBased) {
    }

    public record Check(
            String code,
            InterviewReadinessCheckStatus status,
            String message) {
    }
}
