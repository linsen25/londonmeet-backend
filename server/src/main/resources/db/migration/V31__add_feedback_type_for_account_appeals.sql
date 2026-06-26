ALTER TABLE user_feedback
    ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'FEEDBACK' AFTER user_id;

CREATE INDEX idx_user_feedback_type_status_created
    ON user_feedback (type, status, created_at);
