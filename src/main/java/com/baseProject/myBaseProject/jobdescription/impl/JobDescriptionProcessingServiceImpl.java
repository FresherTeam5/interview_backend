package com.baseProject.myBaseProject.jobdescription.impl;

import com.baseProject.myBaseProject.ai.support.AiExecutionMetadata;
import com.baseProject.myBaseProject.config.AsyncConfig;
import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.JobDescriptionAnalysisResult;
import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.JobDescriptionAnalysisService;
import com.baseProject.myBaseProject.jobdescription.JobDescriptionProcessingService;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionAnalysisResultRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.storage.StorageService;
import com.baseProject.myBaseProject.service.NotificationService;
import com.baseProject.myBaseProject.enums.UserNotificationType;
import com.baseProject.myBaseProject.util.pdf.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
public class JobDescriptionProcessingServiceImpl implements JobDescriptionProcessingService {
    private final JobDescriptionDocumentRepository documents;
    private final JobDescriptionAnalysisResultRepository results;
    private final InterviewTemplateRepository templates;
    private final JobDescriptionAnalysisService analysisService;
    private final JobAnalysisJsonMapper analysisJsonMapper;
    private final PdfTextExtractor pdfTextExtractor;
    private final StorageService storage;
    private final JobDescriptionProperties properties;
    private final ChatModel chatModel;
    private final Clock clock;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;

    public JobDescriptionProcessingServiceImpl(
            JobDescriptionDocumentRepository documents,
            JobDescriptionAnalysisResultRepository results,
            InterviewTemplateRepository templates,
            JobDescriptionAnalysisService analysisService,
            JobAnalysisJsonMapper analysisJsonMapper,
            PdfTextExtractor pdfTextExtractor,
            StorageService storage,
            JobDescriptionProperties properties,
            ChatModel chatModel,
            Clock clock,
            NotificationService notifications,
            PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.results = results;
        this.templates = templates;
        this.analysisService = analysisService;
        this.analysisJsonMapper = analysisJsonMapper;
        this.pdfTextExtractor = pdfTextExtractor;
        this.storage = storage;
        this.properties = properties;
        this.chatModel = chatModel;
        this.clock = clock;
        this.notifications = notifications;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    @Async(AsyncConfig.JD_PROCESS_EXECUTOR)
    public void processAsync(Long jobDescriptionId) {
        try {
            WorkItem item = claim(jobDescriptionId);
            if (item == null) {
                return;
            }
            String extractedText = extract(item);
            if (item.sourceType() == JobDescriptionSourceType.FILE
                    && !markAnalyzing(jobDescriptionId)) {
                return;
            }
            long startedAt = System.currentTimeMillis();
            JobAnalysis analysis = analysisService.analyze(item.displayName(), extractedText);
            int durationMs = AiExecutionMetadata.toNonNegativeInt(
                    System.currentTimeMillis() - startedAt);

            if (persist(jobDescriptionId, extractedText, analysis, durationMs)) {
                notifySafely(item.userId(), UserNotificationType.JOB_DESCRIPTION_READY,
                        "Job description analysis completed",
                        "Your job description has been analyzed and its interview template is ready.",
                        jobDescriptionId);
            }
        } catch (DomainException exception) {
            notifyFailure(jobDescriptionId,
                    markFailed(jobDescriptionId, exception.getCode(), exception.getMessage()));
        } catch (IOException exception) {
            log.warn("Cannot extract text from job description id={}", jobDescriptionId, exception);
            notifyFailure(jobDescriptionId, markFailed(jobDescriptionId,
                    ErrorCode.JD_PROCESSING_FAILED,
                    ErrorCode.JD_PROCESSING_FAILED.getDefaultMessage()));
        } catch (RuntimeException exception) {
            log.error("Job description processing failed, id={}", jobDescriptionId, exception);
            notifyFailure(jobDescriptionId, markFailed(jobDescriptionId,
                    ErrorCode.JD_PROCESSING_FAILED,
                    ErrorCode.JD_PROCESSING_FAILED.getDefaultMessage()));
        }
    }

    private WorkItem claim(Long id) {
        return transactions.execute(status -> {
            JobDescriptionDocument document = documents.findByIdForUpdate(id).orElse(null);
            if (document == null || !document.isActive()
                    || document.getStatus() != JobDescriptionStatus.UPLOADED) {
                return null;
            }
            if (results.existsByJobDescriptionId(id)
                    && templates.findBySourceJobDescriptionId(id).isPresent()) {
                document.markReady(clock.instant());
                return null;
            }
            if (document.getSourceType() == JobDescriptionSourceType.FILE) {
                document.markExtracting();
            } else {
                document.markAnalyzing();
            }

            return new WorkItem(document.getSourceType(), document.getStorageKey(),
                    document.getSourceText(), document.getOriginalFilename(),
                    document.getOwner().getId());
        });
    }

    private String extract(WorkItem item) throws IOException {
        String text = item.sourceType() == JobDescriptionSourceType.FILE
                ? pdfTextExtractor.extract(storage.download(item.storageKey()))
                : item.sourceText();
        if (text == null || text.isBlank()) {
            throw new DomainException(ErrorCode.JD_EMPTY_TEXT);
        }
        String normalized = text.strip();
        if (normalized.length() > properties.maxTextCharacters()) {
            throw new DomainException(ErrorCode.JD_TEXT_TOO_LONG,
                    "Extracted job description must not exceed %d characters"
                            .formatted(properties.maxTextCharacters()));
        }

        return normalized;
    }

    private boolean markAnalyzing(Long id) {
        Boolean changed = transactions.execute(status -> {
            JobDescriptionDocument document = documents.findByIdForUpdate(id).orElse(null);
            if (document == null || !document.isActive()
                    || document.getStatus() != JobDescriptionStatus.EXTRACTING) {
                return false;
            }
            document.markAnalyzing();
            return true;
        });

        return Boolean.TRUE.equals(changed);
    }

    private boolean persist(Long id, String extractedText, JobAnalysis analysis, int durationMs) {
        Instant now = clock.instant();
        Boolean persisted = transactions.execute(status -> {
            JobDescriptionDocument document = documents.findByIdForUpdate(id)
                    .orElseThrow(() -> new DomainException(ErrorCode.JD_NOT_FOUND));
            if (!document.isActive() || document.getStatus() != JobDescriptionStatus.ANALYZING) {
                return false;
            }
            if (results.existsByJobDescriptionId(id)
                    || templates.findBySourceJobDescriptionId(id).isPresent()) {
                document.markReady(now);
                return false;
            }
            String json = analysisJsonMapper.toJson(analysis);
            results.save(JobDescriptionAnalysisResult.builder()
                    .jobDescription(document)
                    .extractedText(extractedText)
                    .analysisJson(json)
                    .schemaVersion(JobAnalysisJsonMapper.SCHEMA_VERSION)
                    .modelName(AiExecutionMetadata.resolveModelName(chatModel))
                    .durationMs(durationMs)
                    .createdAt(now)
                    .build());

            InterviewTemplate template = new InterviewTemplate();
            template.setOwner(document.getOwner());
            template.setSourceJobDescription(document);
            template.setTitle(defaultTitle(analysis, document.getOriginalFilename()));
            template.setJobTitle(analysis.jobTitle());
            template.setTargetSeniority(analysis.targetSeniority());
            template.setContentJson(json);
            template.setContentSchemaVersion(JobAnalysisJsonMapper.SCHEMA_VERSION);
            template.setCreatedAt(now);
            template.setUpdatedAt(now);
            templates.save(template);
            document.markReady(now);
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private Long markFailed(Long id, ErrorCode code, String message) {
        try {
            return transactions.execute(status -> {
                JobDescriptionDocument document = documents.findByIdForUpdate(id).orElse(null);
                if (document != null) {
                        if (document.isProcessing()) {
                            document.markFailed(code.name(), message);
                            return document.getOwner().getId();
                        }
                }
                return null;
            });
        } catch (RuntimeException exception) {
            log.error("Cannot persist FAILED status for job description id={}", id, exception);
            return null;
        }
    }

    private void notifyFailure(Long id, Long userId) {
        if (userId != null) {
            notifySafely(userId, UserNotificationType.JOB_DESCRIPTION_FAILED,
                    "Job description analysis failed",
                    "We could not analyze your job description. You can retry from the job description page.",
                    id);
        }
    }

    private void notifySafely(Long userId, UserNotificationType type,
                              String title, String message, Long resourceId) {
        try {
            notifications.create(userId, type, title, message,
                    "JOB_DESCRIPTION", resourceId);
        } catch (RuntimeException exception) {
            log.warn("Cannot create job description notification, id={}", resourceId, exception);
        }
    }

    private String defaultTitle(JobAnalysis analysis, String filename) {
        if (analysis.jobTitle() != null && !analysis.jobTitle().isBlank()) {
            return analysis.jobTitle().strip();
        }
        String value = filename == null ? "Interview template" : filename.strip();
        if (value.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || value.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            value = value.substring(0, value.length() - 4).strip();
        }
        if (value.isBlank()) {
            value = "Interview template";
        }

        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private record WorkItem(JobDescriptionSourceType sourceType, String storageKey,
                            String sourceText, String displayName, Long userId) {
    }
}
