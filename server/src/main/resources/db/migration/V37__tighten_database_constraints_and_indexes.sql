-- Align the physical schema with the current business model before deployment.
-- author_user_id was introduced by an old local migration and is no longer used;
-- creator_user_id is the canonical activity owner column.
SET @drop_author_user_id = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'activities'
          AND column_name = 'author_user_id'
    ) > 0,
    'ALTER TABLE activities DROP COLUMN author_user_id',
    'SELECT 1'
);
PREPARE stmt FROM @drop_author_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE activities
    ADD CONSTRAINT fk_activities_creator_user
        FOREIGN KEY (creator_user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_activities_tag
        FOREIGN KEY (tag_id) REFERENCES tags(id);

ALTER TABLE activity_reports
    ADD INDEX idx_activity_reports_activity_id (activity_id),
    ADD CONSTRAINT fk_activity_reports_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_activity_reports_activity
        FOREIGN KEY (activity_id) REFERENCES activities(id),
    ADD CONSTRAINT fk_activity_reports_reported_user
        FOREIGN KEY (reported_user_id) REFERENCES users(id);

CREATE INDEX idx_activities_status_end_at
    ON activities (status, end_at);

CREATE INDEX idx_activities_status_qr_expires_at
    ON activities (status, qr_expires_at);

CREATE INDEX idx_activity_registrations_status_activity_created
    ON activity_registrations (status, activity_id, created_at);

CREATE INDEX idx_admin_audit_action_created
    ON admin_audit_logs (action_type, created_at);
