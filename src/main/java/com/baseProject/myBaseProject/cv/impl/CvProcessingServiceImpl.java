package com.baseProject.myBaseProject.cv.impl;

import com.baseProject.myBaseProject.ai.support.AiExecutionMetadata;
import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.cv.CvParsingService;
import com.baseProject.myBaseProject.cv.CvProcessingService;
import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.CvParseResult;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.CvParseResultRepository;
import com.baseProject.myBaseProject.service.CandidateProfileService;
import com.baseProject.myBaseProject.service.NotificationService;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
public class CvProcessingServiceImpl implements CvProcessingService {

    private static final String SCHEMA_VERSION = "v1";

    private final CvDocumentRepository cvDocumentRepository;
    private final CvParseResultRepository cvParseResultRepository;
    private final StorageService storageService;
    private final CvParsingService cvParsingService;
    private final CandidateProfileService candidateProfileService;
    private final NotificationService notifications;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public CvProcessingServiceImpl(CvDocumentRepository cvDocumentRepository,
                                   CvParseResultRepository cvParseResultRepository,
                                   StorageService storageService,
                                   CvParsingService cvParsingService,
                                   CandidateProfileService candidateProfileService,
                                   NotificationService notifications,
                                   ChatModel chatModel,
                                   ObjectMapper objectMapper,
                                   Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.cvDocumentRepository = cvDocumentRepository;
        this.cvParseResultRepository = cvParseResultRepository;
        this.storageService = storageService;
        this.cvParsingService = cvParsingService;
        this.candidateProfileService = candidateProfileService;
        this.notifications = notifications;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Async(AsyncConfig.CV_PARSE_EXECUTOR)
    public void processAsync(Long cvDocumentId) {
        Long userId = null;
        try {
            WorkItem workItem = claim(cvDocumentId);
            if (workItem == null) {
                return;
            }
            userId = workItem.userId();

            byte[] content = storageService.download(workItem.storageKey());
            long startedAt = System.currentTimeMillis();
            CvExtractionResult extraction = cvParsingService.parseCvFromPdf(content);
            int durationMs = AiExecutionMetadata.toNonNegativeInt(
                    System.currentTimeMillis() - startedAt);

            persistResult(cvDocumentId, extraction, durationMs);
            notifySafely(userId, UserNotificationType.CV_READY,
                    "CV analysis completed",
                    "Your CV has been analyzed and the candidate profile is ready.", cvDocumentId);
        } catch (RuntimeException e) {
            log.error("Background CV processing failed, cvDocumentId={}", cvDocumentId, e);
            Long failedUserId = markFailed(
                    cvDocumentId, ErrorCode.CV_PARSE_FAILED.getDefaultMessage());
            if (failedUserId != null) {
                notifySafely(failedUserId, UserNotificationType.CV_FAILED,
                        "CV analysis failed",
                        "We could not analyze your CV. You can retry from the CV page.", cvDocumentId);
            }
        }
    }

    private WorkItem claim(Long cvDocumentId) {
        // Transaction ngắn chỉ nhận job; không giữ transaction trong lúc tải file và gọi AI.
        return transactionTemplate.execute(status -> {
            CvDocument document = cvDocumentRepository.findById(cvDocumentId).orElse(null);
            if (document == null || !document.isActive()
                    || document.getStatus() != CvDocumentStatus.UPLOADED) {
                return null;
            }

            if (cvParseResultRepository.existsByCvDocumentId(cvDocumentId)) {
                document.markParsed(clock.instant());
                return null;
            }

            document.markParsing();
            return new WorkItem(document.getStorageKey(), document.getUser().getId());
        });
    }

    private void persistResult(Long cvDocumentId,
                               CvExtractionResult extraction,
                               int measuredDurationMs) {
        Instant now = clock.instant();
        // Bản AI gốc và profile có thể sửa phải được cho vào transaction
        transactionTemplate.executeWithoutResult(status -> {
            CvDocument document = cvDocumentRepository.findById(cvDocumentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "CV document disappeared while it was being parsed"));

            if (cvParseResultRepository.existsByCvDocumentId(cvDocumentId)) {
                document.markParsed(now);
                return;
            }

            CvParseResult parseResult = CvParseResult.builder()
                    .cvDocument(document)
                    .rawJson(toJson(extraction))
                    .schemaVersion(SCHEMA_VERSION)
                    .modelName(AiExecutionMetadata.resolveModelName(
                            extraction.modelName(), chatModel))
                    .durationMs(extraction.durationMs() == null
                            ? measuredDurationMs
                            : AiExecutionMetadata.toNonNegativeInt(extraction.durationMs()))
                    .tokenCost(extraction.totalTokens() == null
                            ? null
                            : AiExecutionMetadata.toNonNegativeInt(extraction.totalTokens()))
                    .createdAt(now)
                    .build();
            cvParseResultRepository.save(parseResult);

            candidateProfileService.createFromParse(document, extraction, now);

            document.markParsed(now);
        });
    }

    private Long markFailed(Long cvDocumentId, String statusMessage) {
        try {
            return transactionTemplate.execute(status -> {
                CvDocument document = cvDocumentRepository.findById(cvDocumentId).orElse(null);
                if (document != null) {
                        // Không để lỗi đến muộn ghi đè một kết quả PARSED đã commit thành công.
                        if (document.getStatus() != CvDocumentStatus.PARSED) {
                            document.markFailed(statusMessage);
                            return document.getUser().getId();
                        }
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.error("Cannot persist FAILED status for cvDocumentId={}", cvDocumentId, e);
            return null;
        }
    }

    private String toJson(CvExtractionResult extraction) {
        try {
            return objectMapper.writeValueAsString(extraction);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize parsed CV result", e);
        }
    }

    private void notifySafely(Long userId, UserNotificationType type,
                              String title, String message, Long resourceId) {
        try {
            notifications.create(userId, type, title, message, "CV_DOCUMENT", resourceId);
        } catch (RuntimeException exception) {
            log.warn("Cannot create CV processing notification, cvDocumentId={}",
                    resourceId, exception);
        }
    }

    private record WorkItem(String storageKey, Long userId) {
    }
}
