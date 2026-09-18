ALTER TABLE interview_templates
    MODIFY COLUMN source_job_description_id BIGINT NULL;

ALTER TABLE candidate_profiles
    MODIFY COLUMN cv_document_id BIGINT NULL;

CREATE TABLE user_template_favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_template_favorite (user_id, template_id),
    KEY idx_template_favorites_template (template_id),
    CONSTRAINT fk_template_favorites_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_template_favorites_template FOREIGN KEY (template_id)
        REFERENCES interview_templates (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE user_template_views (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    last_viewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_template_view (user_id, template_id),
    KEY idx_template_views_recent (user_id, last_viewed_at),
    CONSTRAINT fk_template_views_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_template_views_template FOREIGN KEY (template_id)
        REFERENCES interview_templates (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_template_views_count CHECK (view_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE user_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    resource_type VARCHAR(40) NULL,
    resource_id BIGINT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_user_unread (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
