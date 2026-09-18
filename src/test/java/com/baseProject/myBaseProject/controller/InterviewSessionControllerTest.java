package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.dto.session.InterviewAnswerResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.InterviewOptionResponse;
import com.baseProject.myBaseProject.dto.session.InterviewReportResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionPageResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;
import com.baseProject.myBaseProject.dto.session.InterviewTurnResponse;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewSessionNextAction;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnInputMode;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.InterviewConversationService;
import com.baseProject.myBaseProject.service.InterviewHistoryService;
import com.baseProject.myBaseProject.service.InterviewReportService;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewSessionControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewSessionService service;

    @MockitoBean
    private InterviewHistoryService historyService;

    @MockitoBean
    private InterviewConversationService conversationService;

    @MockitoBean
    private InterviewReportService reportService;

    @Test
    void authenticatedUserCanCreateSession() throws Exception {
        when(service.create(eq(7L), eq("request-1"), any()))
                .thenReturn(response(InterviewSessionStatus.PREPARING));

        mockMvc.perform(post("/api/interview-sessions")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": 101,
                                  "profileId": 35,
                                  "languageCode": "vi",
                                  "durationMinutes": 30,
                                  "interviewerStyle": "PROFESSIONAL"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.durationMinutes").value(30));

        verify(service).create(eq(7L), eq("request-1"), any());
    }

    @Test
    void userCanListInterviewHistory() throws Exception {
        when(historyService.list(
                eq(7L), eq("backend"), eq(InterviewSessionStatus.COMPLETED),
                eq(InterviewSessionMode.TURN_BASED), eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(new InterviewSessionPageResponse(
                        List.of(new InterviewSessionSummaryResponse(
                                501L,
                                InterviewSessionStatus.COMPLETED,
                                InterviewSessionNextAction.VIEW_REPORT,
                                "Backend Java",
                                "Minh profile",
                                "vi",
                                30,
                                InterviewerStyle.PROFESSIONAL,
                                InterviewSessionMode.TURN_BASED,
                                new BigDecimal("74.00"),
                                new BigDecimal("75.00"),
                                new BigDecimal("70.00"),
                                NOW,
                                NOW.plusSeconds(1800),
                                InterviewEndReason.AI_COMPLETED,
                                NOW.plusSeconds(1200),
                                NOW.plusSeconds(1210),
                                NOW.minusSeconds(60),
                                NOW.plusSeconds(1210))),
                        0,
                        20,
                        1,
                        1));

        mockMvc.perform(get("/api/interview-sessions")
                        .with(user(userDetails()))
                        .param("keyword", "backend")
                        .param("status", "COMPLETED")
                        .param("mode", "TURN_BASED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(501))
                .andExpect(jsonPath("$.items[0].nextAction").value("VIEW_REPORT"))
                .andExpect(jsonPath("$.items[0].overallScore").value(74.0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void invalidCreateBodyDoesNotReachService() throws Exception {
        mockMvc.perform(post("/api/interview-sessions")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": 101,
                                  "languageCode": "vi",
                                  "durationMinutes": 30,
                                  "interviewerStyle": "PROFESSIONAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.profileId").exists());

        verifyNoInteractions(service);
    }

    @Test
    void userCanReadAvailableOptions() throws Exception {
        when(service.options()).thenReturn(new InterviewSessionOptionsResponse(
                List.of(new InterviewOptionResponse("vi", "Tiếng Việt")),
                List.of(15, 30),
                List.of(new InterviewOptionResponse("PROFESSIONAL", "Chuyên nghiệp")),
                List.of(new InterviewOptionResponse(
                        "VOICE_REALTIME", "Giọng nói thời gian thực"))));

        mockMvc.perform(get("/api/interview-session-options").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[0].code").value("vi"))
                .andExpect(jsonPath("$.durations[1]").value(30))
                .andExpect(jsonPath("$.interviewerStyles[0].code")
                        .value("PROFESSIONAL"));
    }

    @Test
    void anonymousUserCannotReadSession() throws Exception {
        mockMvc.perform(get("/api/interview-sessions/501"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    @Test
    void statusResponseDoesNotExposeInternalAiContext() throws Exception {
        when(service.get(7L, 501L)).thenReturn(response(InterviewSessionStatus.READY));

        mockMvc.perform(get("/api/interview-sessions/501").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.jobContextSummary").doesNotExist())
                .andExpect(jsonPath("$.candidateContextSummary").doesNotExist())
                .andExpect(jsonPath("$.openingMessage").doesNotExist())
                .andExpect(jsonPath("$.focusAreas").doesNotExist());
    }

    @Test
    void userCanStartPreparedInterview() throws Exception {
        when(conversationService.start(7L, 501L)).thenReturn(conversationResponse());

        mockMvc.perform(post("/api/interview-sessions/501/start")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deadlineAt").value("2026-09-06T08:30:00Z"))
                .andExpect(jsonPath("$.turns[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.turns[0].action").value("OPENING"));
    }

    @Test
    void userCanSubmitAnswerForCurrentTurn() throws Exception {
        InterviewTurnResponse candidate = new InterviewTurnResponse(
                2L, 1, InterviewTurnRole.CANDIDATE, InterviewTurnInputMode.TEXT,
                "Tôi xây dựng REST API.",
                CandidateIntent.ANSWER,
                null, null, "answer-1",
                com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus.COMPLETED,
                null, false, null, NOW.plusSeconds(20));
        InterviewTurnResponse interviewer = new InterviewTurnResponse(
                3L, 2, InterviewTurnRole.INTERVIEWER,
                InterviewTurnInputMode.TEXT,
                "Bạn đã xử lý lỗi API đó như thế nào?",
                null,
                InterviewTurnAction.FOLLOW_UP, "BACKEND",
                null, null, null, false, null, NOW.plusSeconds(21));
        when(conversationService.answer(eq(7L), eq(501L), eq("answer-1"), any()))
                .thenReturn(new InterviewAnswerResponse(
                        501L,
                        InterviewSessionStatus.IN_PROGRESS,
                        NOW.plusSeconds(1800),
                        null,
                        null,
                        1780,
                        2,
                        candidate,
                        interviewer));

        mockMvc.perform(post("/api/interview-sessions/501/answers")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "answer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedTurnIndex": 0,
                                  "answer": "Tôi xây dựng REST API.",
                                  "inputMode": "TEXT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTurnIndex").value(2))
                .andExpect(jsonPath("$.candidateTurn.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.candidateTurn.candidateIntent").value("ANSWER"))
                .andExpect(jsonPath("$.interviewerTurn.action").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.interviewerTurn.focusAreaCode").value("BACKEND"));
    }

    @Test
    void finishReturnsReasonForFrontendCompletionScreen() throws Exception {
        InterviewConversationResponse response = new InterviewConversationResponse(
                501L,
                InterviewSessionStatus.SCORING,
                InterviewSessionMode.TURN_BASED,
                null,
                null,
                NOW,
                NOW.plusSeconds(1800),
                InterviewEndReason.CANDIDATE_FINISHED,
                NOW.plusSeconds(600),
                0,
                0,
                conversationResponse().turns());
        when(conversationService.finish(7L, 501L)).thenReturn(response);

        mockMvc.perform(post("/api/interview-sessions/501/finish")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORING"))
                .andExpect(jsonPath("$.endReason").value("CANDIDATE_FINISHED"))
                .andExpect(jsonPath("$.endedAt").value("2026-09-06T08:10:00Z"))
                .andExpect(jsonPath("$.remainingSeconds").value(0))
                .andExpect(jsonPath("$.turns.length()").value(1));
    }

    @Test
    void invalidAnswerDoesNotReachConversationService() throws Exception {
        mockMvc.perform(post("/api/interview-sessions/501/answers")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "answer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(conversationService);
    }

    @Test
    void userCanReadCompletedInterviewReport() throws Exception {
        when(reportService.get(7L, 501L)).thenReturn(reportResponse());

        mockMvc.perform(get("/api/interview-sessions/501/report")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.report.score").value(74.0))
                .andExpect(jsonPath("$.report.scores.technical.score").value(75.0))
                .andExpect(jsonPath("$.report.scores.technical.feedback")
                        .value("Kiến thức nền tốt, cần giải thích trade-off rõ hơn."))
                .andExpect(jsonPath("$.report.scores.communication.score").value(70.0))
                .andExpect(jsonPath("$.report.scores.communication.feedback")
                        .value("Câu trả lời rõ ràng nhưng đôi lúc thiếu cấu trúc."))
                .andExpect(jsonPath("$.report.focusAreas[0].name").value("Backend"))
                .andExpect(jsonPath("$.report.recommendations[0]")
                        .value("Luyện phân tích trade-off bằng ví dụ thực tế."));
    }

    @Test
    void retryScoringReturnsAcceptedStatus() throws Exception {
        InterviewReportResponse response = new InterviewReportResponse(
                501L, InterviewSessionStatus.SCORING,
                null, null, null, null);
        when(reportService.retryScoring(7L, 501L)).thenReturn(response);

        mockMvc.perform(post("/api/interview-sessions/501/scoring/retry")
                        .with(user(userDetails())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SCORING"));
    }

    private InterviewSessionStatusResponse response(InterviewSessionStatus status) {
        return new InterviewSessionStatusResponse(
                501L, status, "Backend Java", "Minh profile", "vi", 30,
                InterviewerStyle.PROFESSIONAL,
                InterviewSessionMode.TURN_BASED, null, null,
                null, null, null, null,
                NOW, null, null, null);
    }

    private InterviewConversationResponse conversationResponse() {
        InterviewTurnResponse opening = new InterviewTurnResponse(
                1L,
                0,
                InterviewTurnRole.INTERVIEWER,
                InterviewTurnInputMode.TEXT,
                "Xin chào, bạn hãy giới thiệu về mình.",
                null,
                InterviewTurnAction.OPENING,
                null,
                null,
                null,
                null,
                false,
                null,
                NOW);
        return new InterviewConversationResponse(
                501L,
                InterviewSessionStatus.IN_PROGRESS,
                InterviewSessionMode.TURN_BASED,
                null,
                null,
                NOW,
                NOW.plusSeconds(1800),
                null,
                null,
                1800,
                0,
                List.of(opening));
    }

    private InterviewReportResponse reportResponse() {
        return new InterviewReportResponse(
                501L,
                InterviewSessionStatus.COMPLETED,
                null,
                null,
                new InterviewReportResponse.Report(
                        new BigDecimal("74.00"),
                        "Ứng viên có nền tảng backend.",
                        new InterviewReportResponse.ScoreBreakdown(
                                new InterviewReportResponse.ScoreFeedback(
                                        new BigDecimal("75.00"),
                                        "Kiến thức nền tốt, cần giải thích trade-off rõ hơn."),
                                new InterviewReportResponse.ScoreFeedback(
                                        new BigDecimal("70.00"),
                                        "Câu trả lời rõ ràng nhưng đôi lúc thiếu cấu trúc.")),
                        List.of(new InterviewReportResponse.FocusAreaScore(
                                "Backend", new BigDecimal("75.00"))),
                        List.of("Luyện phân tích trade-off bằng ví dụ thực tế.")),
                NOW);
    }

    private CustomUserDetails userDetails() {
        return new CustomUserDetails(UserAccount.builder()
                .id(7L)
                .email("candidate@example.com")
                .passwordHash("password")
                .role(UserRole.USER)
                .enabled(true)
                .build());
    }
}
