package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.jobdescription.validation.JobAnalysisValidator;
import com.baseProject.myBaseProject.mapper.InterviewTemplateMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InterviewTemplateServiceImplTest {
    @Test
    void confirmedTemplateCannotBeEdited() {
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        JobAnalysisJsonMapper analysisJsonMapper = new JobAnalysisJsonMapper(objectMapper);
        JobDescriptionDocument source = JobDescriptionDocument.builder().id(4L).build();
        InterviewTemplate template = new InterviewTemplate();
        template.setId(9L);
        template.setOwner(UserAccount.builder().id(2L).build());
        template.setSourceJobDescription(source);
        template.setTitle("Backend interview");
        template.setContentJson(analysisJsonMapper.toJson(content()));
        template.setContentSchemaVersion("v2");
        template.setCreatedAt(Instant.parse("2026-09-05T00:00:00Z"));
        template.setUpdatedAt(Instant.parse("2026-09-05T00:00:00Z"));
        when(templates.findOwnedForUpdate(9L, 2L)).thenReturn(Optional.of(template));

        var service = new InterviewTemplateServiceImpl(
                templates, mock(UserAccountRepository.class),
                mock(com.baseProject.myBaseProject.repository.TemplateFavoriteRepository.class),
                mock(com.baseProject.myBaseProject.repository.TemplateViewRepository.class),
                new InterviewTemplateMapper(analysisJsonMapper), analysisJsonMapper,
                new JobAnalysisValidator(),
                Clock.fixed(Instant.parse("2026-09-05T01:00:00Z"), ZoneOffset.UTC));

        var edited = service.update(2L, 9L,
                new UpdateInterviewTemplateRequest("Edited", content(), 0L));
        assertThat(edited.title()).isEqualTo("Edited");
        assertThat(service.confirm(2L, 9L, 0L).confirmed())
                .isTrue();
        assertThatThrownBy(() -> service.update(2L, 9L,
                new UpdateInterviewTemplateRequest("Another edit", content(), 0L)))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.TEMPLATE_ALREADY_CONFIRMED));
    }

    @Test
    void listsEveryOwnedTemplateForExportWithoutRecordingRecentViews() {
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        var views = mock(com.baseProject.myBaseProject.repository.TemplateViewRepository.class);
        JobAnalysisJsonMapper json = new JobAnalysisJsonMapper(new ObjectMapper());
        var sort = Sort.by(Sort.Direction.DESC, "createdAt");
        var firstPage = PageRequest.of(0, 100, sort);
        var secondPage = PageRequest.of(1, 100, sort);
        when(templates.findByOwnerId(2L, firstPage)).thenReturn(
                new PageImpl<>(List.of(template(9L, json)), firstPage, 101));
        when(templates.findByOwnerId(2L, secondPage)).thenReturn(
                new PageImpl<>(List.of(template(8L, json)), secondPage, 101));
        var service = new InterviewTemplateServiceImpl(
                templates, mock(UserAccountRepository.class),
                mock(com.baseProject.myBaseProject.repository.TemplateFavoriteRepository.class),
                views, new InterviewTemplateMapper(json), json,
                new JobAnalysisValidator(), Clock.systemUTC());

        var result = service.listOwnedDetails(2L);

        assertThat(result).extracting("id").containsExactly(9L, 8L);
        verifyNoInteractions(views);
    }

    private InterviewTemplate template(long id, JobAnalysisJsonMapper json) {
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        InterviewTemplate template = new InterviewTemplate();
        template.setId(id);
        template.setOwner(UserAccount.builder().id(2L).build());
        template.setTitle("Backend interview " + id);
        template.setContentJson(json.toJson(content()));
        template.setContentSchemaVersion("v2");
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return template;
    }

    private JobAnalysis content() {
        return new JobAnalysis(
                true, "vi", "Backend", "Middle", "IT", "Backend role",
                List.of(new JobAnalysis.KeySkill("API Design", JobAnalysis.SkillLevel.MUST_HAVE, "Build RESTful APIs")));
    }
}
