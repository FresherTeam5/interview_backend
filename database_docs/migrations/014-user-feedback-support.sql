CREATE TABLE interview_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_rating TINYINT NULL,
    voice_rating TINYINT NULL,
    report_rating TINYINT NULL,
    comment VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_interview_feedback_session_user (session_id, user_id),
    KEY idx_interview_feedback_user (user_id, created_at),
    CONSTRAINT fk_interview_feedback_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_interview_feedback_session FOREIGN KEY (session_id)
        REFERENCES interview_sessions (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_interview_feedback_ratings CHECK (
        (question_rating IS NULL OR question_rating BETWEEN 1 AND 5)
        AND (voice_rating IS NULL OR voice_rating BETWEEN 1 AND 5)
        AND (report_rating IS NULL OR report_rating BETWEEN 1 AND 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE support_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_code VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    turn_id BIGINT NULL,
    ticket_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    context_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_support_ticket_reference (reference_code),
    KEY idx_support_ticket_user_created (user_id, created_at),
    KEY idx_support_ticket_status_created (status, created_at),
    CONSTRAINT fk_support_ticket_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_support_ticket_session FOREIGN KEY (session_id)
        REFERENCES interview_sessions (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_support_ticket_turn FOREIGN KEY (turn_id)
        REFERENCES interview_turns (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_support_ticket_type CHECK (
        ticket_type IN ('GENERAL', 'TECHNICAL', 'INTERVIEW', 'VOICE', 'REPORT', 'ACCOUNT')),
    CONSTRAINT chk_support_ticket_status CHECK (
        status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
