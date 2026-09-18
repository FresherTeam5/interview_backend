ALTER TABLE user_accounts
    ADD COLUMN email_verified_at DATETIME(6) NULL AFTER enabled,
    ADD COLUMN deletion_requested_at DATETIME(6) NULL AFTER email_verified_at;

UPDATE user_accounts
SET email_verified_at = created_at
WHERE email_verified_at IS NULL;

ALTER TABLE refresh_tokens
    ADD COLUMN user_agent VARCHAR(500) NULL AFTER revoked_at,
    ADD COLUMN ip_address VARCHAR(64) NULL AFTER user_agent,
    ADD COLUMN last_used_at DATETIME(6) NULL AFTER ip_address;

UPDATE refresh_tokens
SET last_used_at = issued_at
WHERE last_used_at IS NULL;

ALTER TABLE refresh_tokens
    MODIFY COLUMN last_used_at DATETIME(6) NOT NULL;

CREATE TABLE account_preferences (
    user_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL DEFAULT 'vi',
    time_zone VARCHAR(50) NOT NULL DEFAULT 'Asia/Bangkok',
    email_notifications BIT(1) NOT NULL DEFAULT b'1',
    processing_notifications BIT(1) NOT NULL DEFAULT b'1',
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_account_preferences_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE account_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_type VARCHAR(30) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_account_tokens_hash (token_hash),
    KEY idx_account_tokens_user_type (user_id, token_type, used_at),
    KEY idx_account_tokens_expires_at (expires_at),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_account_tokens_type CHECK (
        token_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE account_deletion_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_account_deletion_user (user_id),
    KEY idx_account_deletion_due (status, scheduled_at),
    CONSTRAINT fk_account_deletion_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_account_deletion_status CHECK (
        status IN ('PENDING', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
