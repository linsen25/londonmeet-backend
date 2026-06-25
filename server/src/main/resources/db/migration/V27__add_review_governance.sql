ALTER TABLE activity_reviews
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' AFTER scores_json,
    ADD COLUMN admin_note VARCHAR(500) NULL AFTER status,
    ADD COLUMN handled_by BIGINT NULL AFTER admin_note,
    ADD COLUMN handled_at DATETIME NULL AFTER handled_by,
    ADD INDEX idx_activity_reviews_status_created (status, created_at);

