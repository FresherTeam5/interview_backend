package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.dto.jobdescription.CreateJobDescriptionTextRequest;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionAnalysisResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.JobDescriptionProcessingService;
import com.baseProject.myBaseProject.jobdescription.validation.JobDescriptionFileValidator;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.mapper.JobDescriptionMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionAnalysisResultRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.JobDescriptionService;
import com.baseProject.myBaseProject.storage.StorageService;
import com.baseProject.myBaseProject.util.FileStorageSupport;
import com.baseProject.myBaseProject.util.Sha256;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobDescriptionServiceImpl implements JobDescriptionService {
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final int MAX_TITLE_LENGTH = 200;

    private final JobDescriptionDocumentRepository documents;
    private final JobDescriptionAnalysisResultRepository analysisResults;
    private final InterviewTemplateRepository templates;
    private final UserAccountRepository users;
    private final JobDescriptionFileValidator fileValidator;
    private final JobDescriptionProcessingService processingService;
    private final JobDescriptionMapper mapper;
    private final JobAnalysisJsonMapper analysisJsonMapper;
    private final StorageService storage;
    private final JobDescriptionProperties properties;
    private final Clock clock;

    @Override
    public UploadResult upload(Long ownerId, MultipartFile file) {
        byte[] content = fileValidator.validateAndRead(file);
        String checksum = Sha256.hex(content);
        UploadResult reused = reuse(ownerId, checksum);
        if (reused != null) {
            return reused;
        }
        ensureCapacity(ownerId);
        String storageKey = "jd/%d/%s.pdf".formatted(ownerId, UUID.randomUUID());
        storage.upload(storageKey, content, FileStorageSupport.PDF_CONTENT_TYPE);
        JobDescriptionDocument document = documents.save(JobDescriptionDocument.builder()
                .owner(users.getReferenceById(ownerId))
                .sourceType(JobDescriptionSourceType.FILE)
                .storageKey(storageKey)
                .originalFilename(FileStorageSupport.sanitizeFilename(
                        file.getOriginalFilename(), "job-description.pdf"))
                .contentType(FileStorageSupport.PDF_CONTENT_TYPE)
                .fileSizeBytes(content.length)
                .checksumSha256(checksum)
                .uploadedAt(clock.instant())
                .build());
        submit(document);

        return new UploadResult(mapper.toResponse(document, null), false);
    }

    @Override
    public UploadResult createFromText(Long ownerId, CreateJobDescriptionTextRequest request) {
        if (request == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        String title = normalizeTitle(request.title());
        String text = normalizeText(request.text());
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String checksum = Sha256.hex(bytes);
        UploadResult reused = reuse(ownerId, checksum);
        if (reused != null) {
            return reused;
        }
        ensureCapacity(ownerId);
        JobDescriptionDocument document = documents.save(JobDescriptionDocument.builder()
                .owner(users.getReferenceById(ownerId))
                .sourceType(JobDescriptionSourceType.TEXT)
                .originalFilename(FileStorageSupport.sanitizeFilename(
                        title + ".txt", "job-description.txt"))
                .contentType(TEXT_CONTENT_TYPE)
                .fileSizeBytes(bytes.length)
                .checksumSha256(checksum)
                .sourceText(text)
                .uploadedAt(clock.instant())
                .build());
        submit(document);

        return new UploadResult(mapper.toResponse(document, null), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDescriptionResponse> list(Long ownerId) {
        List<JobDescriptionDocument> owned = documents
                .findByOwnerIdAndActiveTrueOrderByUploadedAtDesc(ownerId);
        Map<Long, InterviewTemplate> templatesByDocument = loadTemplates(owned);
        return owned.stream().map(document -> mapper.toResponse(
                document, templatesByDocument.get(document.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JobDescriptionResponse get(Long ownerId, Long id) {
        JobDescriptionDocument document = requireActive(ownerId, id);
        InterviewTemplate template = templates.findBySourceJobDescriptionId(id).orElse(null);

        return mapper.toResponse(document, template);
    }

    @Override
    @Transactional(readOnly = true)
    public JobDescriptionAnalysisResponse analysis(Long ownerId, Long id) {
        JobDescriptionDocument document = requireActive(ownerId, id);
        var result = analysisResults.findByJobDescriptionId(document.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.JD_ANALYSIS_NOT_READY));

        return new JobDescriptionAnalysisResponse(
                document.getId(), result.getExtractedText(),
                analysisJsonMapper.fromJson(result.getAnalysisJson()), result.getSchemaVersion(),
                result.getModelName(), result.getDurationMs(), result.getTokenCount(),
                result.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public JobDescriptionFileUrlResponse fileUrl(Long ownerId, Long id) {
        JobDescriptionDocument document = documents.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new DomainException(ErrorCode.JD_NOT_FOUND));
        if (document.getSourceType() != JobDescriptionSourceType.FILE) {
            throw new DomainException(ErrorCode.JD_FILE_NOT_AVAILABLE);
        }
        String url = storage.generatePresignedUrl(
                document.getStorageKey(), FileStorageSupport.PRESIGNED_URL_TTL);

        return new JobDescriptionFileUrlResponse(
                url, clock.instant().plus(FileStorageSupport.PRESIGNED_URL_TTL));
    }

    @Override
    public JobDescriptionResponse retry(Long ownerId, Long id) {
        JobDescriptionDocument document = requireActive(ownerId, id);
        if (document.getStatus() != JobDescriptionStatus.FAILED) {
            throw new DomainException(document.isProcessing()
                    ? ErrorCode.JD_PROCESSING_IN_PROGRESS
                    : ErrorCode.JD_PROCESSING_NOT_RETRYABLE);
        }
        document.prepareForRetry();
        document = documents.save(document);
        submit(document);

        return mapper.toResponse(document, null);
    }

    @Override
    @Transactional
    public void delete(Long ownerId, Long id) {
        JobDescriptionDocument document = requireActive(ownerId, id);
        if (document.isProcessing()) {
            throw new DomainException(ErrorCode.JD_PROCESSING_IN_PROGRESS);
        }
        document.deactivate();
    }

    private UploadResult reuse(Long ownerId, String checksum) {
        InterviewTemplate template = templates
                .findReusable(ownerId, checksum, JobDescriptionStatus.READY, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        if (template == null) {
            return null;
        }
        JobDescriptionDocument document = template.getSourceJobDescription();
        if (!document.isActive()) {
            ensureCapacity(ownerId);
            document.reactivate();
            document = documents.save(document);
        }
        return new UploadResult(mapper.toResponse(document, template), true);
    }

    private void submit(JobDescriptionDocument document) {
        try {
            processingService.processAsync(document.getId());
        } catch (TaskRejectedException exception) {
            log.warn("Job description queue is full, id={}", document.getId());
            document.markFailed(ErrorCode.AI_SERVICE_UNAVAILABLE.name(),
                    "Job description processing queue is full; please retry later");
            documents.save(document);
        }
    }

    private JobDescriptionDocument requireActive(Long ownerId, Long id) {
        return documents.findByIdAndOwnerIdAndActiveTrue(id, ownerId)
                .orElseThrow(() -> new DomainException(ErrorCode.JD_NOT_FOUND));
    }

    private void ensureCapacity(Long ownerId) {
        if (documents.countByOwnerIdAndActiveTrue(ownerId) >= properties.maxPerUser()) {
            throw new DomainException(ErrorCode.JD_LIMIT_REACHED);
        }
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_TITLE_LENGTH) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return value.strip();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        String normalized = value.strip();
        if (normalized.length() > properties.maxTextCharacters()) {
            throw new DomainException(ErrorCode.JD_TEXT_TOO_LONG);
        }
        return normalized;
    }

    private Map<Long, InterviewTemplate> loadTemplates(List<JobDescriptionDocument> owned) {
        if (owned.isEmpty()) {
            return Map.of();
        }
        Map<Long, InterviewTemplate> byDocument = new HashMap<>();
        templates.findBySourceJobDescriptionIdIn(
                        owned.stream().map(JobDescriptionDocument::getId).toList())
                .forEach(template -> byDocument.put(
                        template.getSourceJobDescription().getId(), template));
        return byDocument;
    }
}
