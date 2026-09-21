CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(100) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    request_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_admin_audit_actor_created (actor_id, created_at),
    KEY idx_admin_audit_resource_created (resource_type, resource_id, created_at),
    KEY idx_admin_audit_action_created (action, created_at),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_id)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE support_tickets
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' AFTER status,
    ADD COLUMN assigned_admin_id BIGINT NULL AFTER priority,
    ADD COLUMN resolution_summary VARCHAR(2000) NULL AFTER context_json,
    ADD COLUMN resolved_at DATETIME(6) NULL AFTER resolution_summary,
    ADD COLUMN closed_at DATETIME(6) NULL AFTER resolved_at,
    ADD KEY idx_support_ticket_assignee_status (assigned_admin_id, status, updated_at),
    ADD CONSTRAINT fk_support_ticket_assignee FOREIGN KEY (assigned_admin_id)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_support_ticket_priority CHECK (
        priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

CREATE TABLE support_ticket_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    sender_id BIGINT NULL,
    visibility VARCHAR(20) NOT NULL,
    message VARCHAR(5000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_support_message_ticket_created (ticket_id, created_at),
    CONSTRAINT fk_support_message_ticket FOREIGN KEY (ticket_id)
        REFERENCES support_tickets (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_support_message_sender FOREIGN KEY (sender_id)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_support_message_visibility CHECK (
        visibility IN ('PUBLIC', 'INTERNAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE support_ticket_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_value VARCHAR(100) NULL,
    to_value VARCHAR(100) NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_support_event_ticket_created (ticket_id, created_at),
    CONSTRAINT fk_support_event_ticket FOREIGN KEY (ticket_id)
        REFERENCES support_tickets (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_support_event_actor FOREIGN KEY (actor_id)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_support_event_type CHECK (
        event_type IN ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'PRIORITY_CHANGED', 'MESSAGE_ADDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE user_accounts
    ADD COLUMN last_login_at DATETIME(6) NULL AFTER deletion_requested_at,
    ADD COLUMN suspended_at DATETIME(6) NULL AFTER last_login_at,
    ADD COLUMN suspended_until DATETIME(6) NULL AFTER suspended_at,
    ADD COLUMN restriction_reason VARCHAR(500) NULL AFTER suspended_until,
    ADD KEY idx_user_last_login (last_login_at),
    ADD KEY idx_user_suspended_until (suspended_until);

ALTER TABLE interview_templates
    ADD COLUMN moderation_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' AFTER archived_at,
    ADD COLUMN submitted_at DATETIME(6) NULL AFTER moderation_status,
    ADD COLUMN reviewed_at DATETIME(6) NULL AFTER submitted_at,
    ADD COLUMN reviewed_by BIGINT NULL AFTER reviewed_at,
    ADD COLUMN moderation_reason VARCHAR(1000) NULL AFTER reviewed_by,
    ADD COLUMN category VARCHAR(80) NULL AFTER moderation_reason,
    ADD COLUMN tags_json JSON NULL AFTER category,
    ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE AFTER tags_json,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER featured,
    ADD KEY idx_template_moderation_created (moderation_status, created_at),
    ADD KEY idx_template_featured_public (featured, display_order, published_at),
    ADD CONSTRAINT fk_template_reviewer FOREIGN KEY (reviewed_by)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT chk_template_moderation_status CHECK (
        moderation_status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'HIDDEN')),
    ADD CONSTRAINT chk_template_display_order CHECK (display_order >= 0);

UPDATE interview_templates
SET moderation_status = 'APPROVED', reviewed_at = published_at
WHERE published_at IS NOT NULL;

CREATE TABLE admin_announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_by BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    audience VARCHAR(30) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL,
    email_enabled BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    scheduled_at DATETIME(6) NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    total_recipients BIGINT NOT NULL DEFAULT 0,
    delivered_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_announcement_status_schedule (status, scheduled_at),
    CONSTRAINT fk_announcement_creator FOREIGN KEY (created_by)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_announcement_audience CHECK (
        audience IN ('ALL_USERS', 'VERIFIED_USERS', 'ACTIVE_USERS')),
    CONSTRAINT chk_announcement_status CHECK (
        status IN ('DRAFT', 'SCHEDULED', 'PROCESSING', 'SENT', 'PARTIALLY_FAILED', 'CANCELLED')),
    CONSTRAINT chk_announcement_channel CHECK (in_app_enabled OR email_enabled),
    CONSTRAINT chk_announcement_counts CHECK (
        total_recipients >= 0 AND delivered_count >= 0 AND failed_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE announcement_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    announcement_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    delivered_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_announcement_delivery_user (announcement_id, user_id),
    KEY idx_announcement_delivery_status (announcement_id, status, id),
    CONSTRAINT fk_announcement_delivery_announcement FOREIGN KEY (announcement_id)
        REFERENCES admin_announcements (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_announcement_delivery_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_announcement_delivery_status CHECK (
        status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_announcement_delivery_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE system_settings (
    setting_key VARCHAR(80) NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    description VARCHAR(500) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_system_setting_updater FOREIGN KEY (updated_by)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_system_setting_type CHECK (
        value_type IN ('BOOLEAN', 'INTEGER', 'STRING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO system_settings
    (setting_key, setting_value, value_type, description, updated_at)
VALUES
    ('ANNOUNCEMENTS_ENABLED', 'true', 'BOOLEAN',
     'Allow scheduled announcements to be dispatched', UTC_TIMESTAMP(6)),
    ('TEMPLATE_REVIEW_REQUIRED', 'true', 'BOOLEAN',
     'Require approval before a template can be published', UTC_TIMESTAMP(6)),
    ('ADMIN_BULK_RETRY_LIMIT', '25', 'INTEGER',
     'Maximum interview sessions accepted by one bulk retry request', UTC_TIMESTAMP(6));

CREATE TABLE background_job_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_name VARCHAR(80) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    triggered_by BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_job_run_name_started (job_name, started_at),
    KEY idx_job_run_status_started (status, started_at),
    CONSTRAINT fk_job_run_admin FOREIGN KEY (triggered_by)
        REFERENCES user_accounts (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_job_run_trigger CHECK (trigger_type IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT chk_job_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_job_run_processed CHECK (processed_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE account_deletion_requests
    ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER cancelled_at,
    ADD COLUMN last_attempt_at DATETIME(6) NULL AFTER attempts,
    ADD COLUMN next_attempt_at DATETIME(6) NULL AFTER last_attempt_at,
    ADD COLUMN last_error VARCHAR(1000) NULL AFTER next_attempt_at,
    ADD KEY idx_account_deletion_retry (status, next_attempt_at),
    ADD CONSTRAINT chk_account_deletion_attempts CHECK (attempts >= 0);

UPDATE account_deletion_requests
SET next_attempt_at = scheduled_at
WHERE status = 'PENDING';
