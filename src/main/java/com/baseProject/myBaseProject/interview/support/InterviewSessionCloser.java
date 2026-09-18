package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.interview.model.InterviewScoringRequestedEvent;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InterviewSessionCloser {
    private final InterviewTurnRepository turns;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final ApplicationEventPublisher events;

    public void close(
            InterviewSession session,
            InterviewEndReason endReason,
            InterviewTransitionActor actor,
            Instant now) {
        InterviewTurn current = turns
                .findBySessionIdAndTurnIndex(session.getId(), session.getCurrentTurnIndex())
                .orElse(null);

        // Candidate turn vẫn hợp lệ dù deadline đến trước khi AI tạo được lượt tiếp theo.
        if (current != null && current.getRole() == InterviewTurnRole.CANDIDATE) {
            current.markCompleted();
        }
        session.beginScoring(endReason, now);
        transitionRecorder.record(
            session,
            InterviewSessionStatus.IN_PROGRESS,
            InterviewSessionStatus.SCORING,
            transitionReason(endReason),
            actor,
            now);

        // Event được publish trong transaction; listener chỉ dispatch scoring sau commit.
        events.publishEvent(new InterviewScoringRequestedEvent(session.getId()));
    }

    private String transitionReason(InterviewEndReason endReason) {
        return switch (endReason) {
            case AI_COMPLETED -> "Interviewer completed interview";
            case TIME_EXPIRED -> "Interview duration reached";
            case CANDIDATE_FINISHED -> "User ended interview";
            case USER_CANCELLED -> "User cancelled interview";
            case SYSTEM_TERMINATED -> "System terminated interview";
        };
    }
}
