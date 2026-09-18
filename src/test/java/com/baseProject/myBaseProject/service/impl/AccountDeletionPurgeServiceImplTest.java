package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountDeletionPurgeServiceImplTest {
    @Test
    void queuesFilesAndDeletesParentRowsInForeignKeySafeOrder() {
        Instant now = Instant.parse("2026-09-18T03:00:00Z");
        AccountDeletionRequestRepository requests = mock(AccountDeletionRequestRepository.class);
        CvDocumentRepository cvs = mock(CvDocumentRepository.class);
        JobDescriptionDocumentRepository jobDescriptions =
                mock(JobDescriptionDocumentRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccountDeletionRequest request = AccountDeletionRequest.builder()
                .id(4L)
                .user(UserAccount.builder().id(7L).build())
                .status(AccountDeletionStatus.PENDING)
                .requestedAt(now.minusSeconds(100))
                .scheduledAt(now.minusSeconds(1))
                .build();
        when(requests.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        when(cvs.findStorageKeysByUserId(7L)).thenReturn(List.of("cv/7/a.pdf"));
        when(jobDescriptions.findStorageKeysByOwnerId(7L)).thenReturn(List.of("jd/7/b.pdf"));
        AccountDeletionPurgeServiceImpl service = new AccountDeletionPurgeServiceImpl(
                requests, cvs, jobDescriptions, jdbc);

        assertThat(service.purgeDueRequest(4L, now)).isTrue();

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "INSERT IGNORE INTO storage_deletion_tasks"), eq("cv/7/a.pdf"), eq(now), eq(now));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "INSERT IGNORE INTO storage_deletion_tasks"), eq("jd/7/b.pdf"), eq(now), eq(now));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_realtime_events"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_voice_connections"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_focus_area_results"), eq(7L), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_assessments"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_feedback"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_focus_areas"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_session_transitions"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "UPDATE interview_turns"), eq(7L));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "DELETE FROM interview_turns"), eq(7L));
        order.verify(jdbc).update("DELETE FROM interview_sessions WHERE user_id = ?", 7L);
        order.verify(jdbc).update("DELETE FROM interview_templates WHERE owner_id = ?", 7L);
        order.verify(jdbc).update("DELETE FROM user_accounts WHERE id = ?", 7L);
    }
}
