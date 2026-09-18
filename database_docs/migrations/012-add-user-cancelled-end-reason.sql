ALTER TABLE interview_sessions
    ADD CONSTRAINT chk_interview_session_end_reason CHECK (
        end_reason IS NULL OR end_reason IN (
            'AI_COMPLETED', 'TIME_EXPIRED', 'CANDIDATE_FINISHED',
            'USER_CANCELLED', 'SYSTEM_TERMINATED'));
