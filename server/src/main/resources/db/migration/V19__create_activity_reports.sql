CREATE TABLE IF NOT EXISTS activity_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    reported_user_id BIGINT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_activity_reports_reporter_activity UNIQUE (reporter_user_id, activity_id),
    INDEX idx_activity_reports_reported_user (reported_user_id),
    INDEX idx_activity_reports_status (status)
);
