package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.AccountDeletionRequest;
import com.baseProject.myBaseProject.enums.AccountDeletionStatus;
import com.baseProject.myBaseProject.repository.AccountDeletionRequestRepository;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import com.baseProject.myBaseProject.service.AccountDeletionPurgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccountDeletionPurgeServiceImpl implements AccountDeletionPurgeService {
    private final AccountDeletionRequestRepository deletionRequests;
    private final CvDocumentRepository cvDocuments;
    private final JobDescriptionDocumentRepository jobDescriptions;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public boolean purgeDueRequest(Long requestId, Instant now) {
        AccountDeletionRequest request = deletionRequests.findByIdForUpdate(requestId).orElse(null);
        if (request == null || request.getStatus() != AccountDeletionStatus.PENDING
                || request.getScheduledAt().isAfter(now)) {
            return false;
        }
        Long userId = request.getUser().getId();
        enqueueStorageDeletion(userId, now);

        deleteInterviewData(userId);
        jdbc.update("DELETE FROM interview_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM interview_templates WHERE owner_id = ?", userId);
        jdbc.update("""
                DELETE FROM job_description_analysis_results
                WHERE job_description_id IN (
                    SELECT id FROM job_description_documents WHERE owner_id = ?)
                """, userId);
        jdbc.update("DELETE FROM job_description_documents WHERE owner_id = ?", userId);

        jdbc.update("""
                DELETE FROM profile_educations
                WHERE profile_id IN (SELECT id FROM candidate_profiles WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM profile_skills
                WHERE profile_id IN (SELECT id FROM candidate_profiles WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM profile_projects
                WHERE profile_id IN (SELECT id FROM candidate_profiles WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM candidate_profiles WHERE user_id = ?", userId);
        jdbc.update("""
                DELETE FROM cv_parse_results
                WHERE cv_document_id IN (SELECT id FROM cv_documents WHERE user_id = ?)
                """, userId);
        jdbc.update("DELETE FROM cv_documents WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_accounts WHERE id = ?", userId);
        return true;
    }

    private void deleteInterviewData(Long userId) {
        jdbc.update("""
                DELETE FROM interview_realtime_events
                WHERE connection_id IN (
                    SELECT id FROM interview_voice_connections
                    WHERE session_id IN (
                        SELECT id FROM interview_sessions WHERE user_id = ?))
                """, userId);
        jdbc.update("""
                DELETE FROM interview_voice_connections
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM interview_focus_area_results
                WHERE assessment_id IN (
                    SELECT id FROM interview_assessments
                    WHERE session_id IN (
                        SELECT id FROM interview_sessions WHERE user_id = ?))
                   OR focus_area_id IN (
                    SELECT id FROM interview_focus_areas
                    WHERE session_id IN (
                        SELECT id FROM interview_sessions WHERE user_id = ?))
                """, userId, userId);
        jdbc.update("""
                DELETE FROM interview_assessments
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM interview_feedback
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM interview_focus_areas
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM interview_session_transitions
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                UPDATE interview_turns
                SET reply_to_turn_id = NULL
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
        jdbc.update("""
                DELETE FROM interview_turns
                WHERE session_id IN (
                    SELECT id FROM interview_sessions WHERE user_id = ?)
                """, userId);
    }

    private void enqueueStorageDeletion(Long userId, Instant now) {
        Set<String> keys = new LinkedHashSet<>(cvDocuments.findStorageKeysByUserId(userId));
        keys.addAll(jobDescriptions.findStorageKeysByOwnerId(userId));
        keys.remove(null);
        keys.forEach(key -> jdbc.update("""
                INSERT IGNORE INTO storage_deletion_tasks(
                    storage_key, attempts, next_attempt_at, created_at)
                VALUES (?, 0, ?, ?)
                """, key, now, now));
    }
}
