CREATE TABLE storage_deletion_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    storage_key VARCHAR(500) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_storage_deletion_key (storage_key),
    KEY idx_storage_deletion_due (next_attempt_at),
    CONSTRAINT chk_storage_deletion_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
