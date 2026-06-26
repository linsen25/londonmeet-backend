ALTER TABLE activity_reviews
    ADD COLUMN reason VARCHAR(300) NULL AFTER scores_json,
    ADD COLUMN batch_good TINYINT(1) NOT NULL DEFAULT 0 AFTER reason;

CREATE INDEX idx_activity_reviews_recent_score
    ON activity_reviews (target_type, status, created_at);
