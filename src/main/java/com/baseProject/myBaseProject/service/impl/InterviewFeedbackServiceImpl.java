package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.feedback.InterviewFeedbackResponse;
import com.baseProject.myBaseProject.dto.feedback.UpsertInterviewFeedbackRequest;
import com.baseProject.myBaseProject.entity.InterviewFeedback;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewFeedbackRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.service.InterviewFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewFeedbackServiceImpl implements InterviewFeedbackService {
    private final InterviewFeedbackRepository feedback;
    private final InterviewSessionRepository sessions;
    private final Clock clock;

    @Override
    public InterviewFeedbackResponse get(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        return feedback.findBySessionIdAndUserId(sessionId, userId)
                .map(this::map)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_FEEDBACK_NOT_FOUND));
    }

    @Override
    @Transactional
    public InterviewFeedbackResponse upsert(
            Long userId, Long sessionId, UpsertInterviewFeedbackRequest request) {
        InterviewSession session = requireOwnedSession(userId, sessionId);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new DomainException(ErrorCode.INTERVIEW_FEEDBACK_NOT_AVAILABLE);
        }
        Instant now = clock.instant();
        InterviewFeedback value = feedback.findBySessionIdAndUserId(sessionId, userId)
                .orElseGet(() -> {
                    InterviewFeedback created = new InterviewFeedback();
                    created.setSession(session);
                    created.setUser(session.getUser());
                    created.setCreatedAt(now);
                    return created;
                });
        value.setQuestionRating(request.questionRating());
        value.setVoiceRating(request.voiceRating());
        value.setReportRating(request.reportRating());
        value.setComment(normalize(request.comment()));
        value.setUpdatedAt(now);
        return map(feedback.save(value));
    }

    private InterviewSession requireOwnedSession(Long userId, Long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private InterviewFeedbackResponse map(InterviewFeedback value) {
        return new InterviewFeedbackResponse(value.getId(), value.getSession().getId(),
                value.getQuestionRating(), value.getVoiceRating(), value.getReportRating(),
                value.getComment(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
