package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.dto.template.CloneInterviewTemplateRequest;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.jobdescription.mapper.JobAnalysisJsonMapper;
import com.baseProject.myBaseProject.jobdescription.validation.JobAnalysisValidator;
import com.baseProject.myBaseProject.mapper.InterviewTemplateMapper;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.TemplateFavoriteRepository;
import com.baseProject.myBaseProject.repository.TemplateViewRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
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

class UserDiscoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-18T03:00:00Z");

    @Test
    void clonesAccessibleTemplateAsIndependentDraftAndFavoritesIdempotently() {
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        TemplateFavoriteRepository favorites = mock(TemplateFavoriteRepository.class);
        TemplateViewRepository views = mock(TemplateViewRepository.class);
        JobAnalysisJsonMapper json = new JobAnalysisJsonMapper(new ObjectMapper());
        UserAccount user = UserAccount.builder().id(7L).build();
        InterviewTemplate source = new InterviewTemplate();
        source.setId(12L);
        source.setTitle("Java backend");
        source.setJobTitle("Backend Engineer");
        source.setTargetSeniority("Middle");
        source.setContentJson(json.toJson(new JobAnalysis(
                true, "vi", "Backend Engineer", "Middle", "Software",
                "Backend role", List.of(new JobAnalysis.KeySkill(
                        "Java", JobAnalysis.SkillLevel.MUST_HAVE, "Build services")))));
        source.setContentSchemaVersion("v2");
        source.setPublishedAt(NOW.minusSeconds(60));
        when(templates.findAccessibleForSession(12L, 7L)).thenReturn(Optional.of(source));
        when(users.getReferenceById(7L)).thenReturn(user);
        when(templates.saveAndFlush(any())).thenAnswer(invocation -> {
            InterviewTemplate clone = invocation.getArgument(0);
            clone.setId(13L);
            return clone;
        });
        InterviewTemplateServiceImpl service = new InterviewTemplateServiceImpl(
                templates, users, favorites, views,
                new InterviewTemplateMapper(json), json, new JobAnalysisValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var clone = service.cloneTemplate(
                7L, 12L, new CloneInterviewTemplateRequest("My practice"));
        var favorite = service.favorite(7L, 12L);

        assertThat(clone.id()).isEqualTo(13L);
        assertThat(clone.sourceJobDescriptionId()).isNull();
        assertThat(clone.title()).isEqualTo("My practice");
        assertThat(clone.confirmed()).isFalse();
        assertThat(clone.published()).isFalse();
        assertThat(favorite.favorite()).isTrue();
        verify(favorites).addForUser(7L, 12L, NOW);
    }
}
