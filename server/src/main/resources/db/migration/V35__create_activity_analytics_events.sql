CREATE TABLE activity_analytics_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL COMMENT 'EXPOSURE, DETAIL_VIEW, QR_OPEN',
    event_hour DATETIME NOT NULL COMMENT 'Hour bucket used for deduplication',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_event_hour (user_id, activity_id, event_type, event_hour),
    KEY idx_activity_events_type_time (event_type, created_at),
    KEY idx_activity_events_activity_time (activity_id, created_at),
    KEY idx_activity_events_user_time (user_id, created_at),
    CONSTRAINT fk_activity_events_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_events_activity
        FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
