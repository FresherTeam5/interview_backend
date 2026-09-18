package com.baseProject.myBaseProject.jobdescription;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.JobDescriptionAnalysisResult;
import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.jobdescription.impl.JobDescriptionProcessingServiceImpl;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionAnalysisResultRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.storage.StorageService;
import com.baseProject.myBaseProject.util.pdf.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobDescriptionProcessingServiceImplTest {
    @Test
    void textInputCreatesImmutableAnalysisAndDraftTemplateAtomically() {
        JobDescriptionDocumentRepository documents = mock(JobDescriptionDocumentRepository.class);
        JobDescriptionAnalysisResultRepository results =
                mock(JobDescriptionAnalysisResultRepository.class);
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        JobDescriptionAnalysisService analysisService =
                mock(JobDescriptionAnalysisService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        UserAccount owner = UserAccount.builder().id(7L).build();
        JobDescriptionDocument document = JobDescriptionDocument.builder()
                .id(11L)
                .owner(owner)
                .sourceType(JobDescriptionSourceType.TEXT)
                .originalFilename("java-backend.txt")
                .contentType("text/plain")
                .fileSizeBytes(JD.length())
                .checksumSha256("a".repeat(64))
                .sourceText(JD)
                .status(JobDescriptionStatus.UPLOADED)
                .active(true)
                .uploadedAt(Instant.parse("2026-09-05T00:00:00Z"))
                .build();
        when(documents.findByIdForUpdate(11L)).thenReturn(Optional.of(document));
        when(results.existsByJobDescriptionId(11L)).thenReturn(false);
        when(templates.findBySourceJobDescriptionId(11L)).thenReturn(Optional.empty());
        when(analysisService.analyze("java-backend.txt", JD)).thenReturn(analysis());

        ObjectMapper objectMapper = new ObjectMapper();
        var service = new JobDescriptionProcessingServiceImpl(
                documents, results, templates, analysisService,
                new JobAnalysisJsonMapper(objectMapper), mock(PdfTextExtractor.class),
                mock(StorageService.class), new JobDescriptionProperties(5_000_000, 20, 50, 30_000),
                mock(ChatModel.class), Clock.fixed(Instant.parse("2026-09-05T01:00:00Z"), ZoneOffset.UTC),
                mock(com.baseProject.myBaseProject.service.NotificationService.class),
                transactionManager);

        service.processAsync(11L);

        var resultCaptor = org.mockito.ArgumentCaptor.forClass(JobDescriptionAnalysisResult.class);
        verify(results).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getExtractedText()).isEqualTo(JD);
        assertThat(resultCaptor.getValue().getSchemaVersion()).isEqualTo("v2");

        var templateCaptor = org.mockito.ArgumentCaptor.forClass(InterviewTemplate.class);
        verify(templates).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getOwner()).isSameAs(owner);
        assertThat(templateCaptor.getValue().getTitle()).isEqualTo("Java Backend Developer");
        assertThat(templateCaptor.getValue().isConfirmed()).isFalse();
        assertThat(document.getStatus()).isEqualTo(JobDescriptionStatus.READY);
    }

    private JobAnalysis analysis() {
        return new JobAnalysis(
                true, "vi", "Java Backend Developer", "Middle", "IT",
                "Phát triển REST API.",
                List.of(
                        new JobAnalysis.KeySkill("Spring Boot", JobAnalysis.SkillLevel.MUST_HAVE, "Phát triển REST API"),
                        new JobAnalysis.KeySkill("REST API", JobAnalysis.SkillLevel.MUST_HAVE, "Thiết kế API")
                ));
    }

    private static final String JD =
            "Java Backend Middle. Phát triển REST API bằng Spring Boot.";
}
