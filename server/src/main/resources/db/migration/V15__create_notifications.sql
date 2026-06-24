CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Notification primary key',
    user_id BIGINT NOT NULL COMMENT 'Receiver user id',
    type VARCHAR(50) NOT NULL COMMENT 'Notification type',
    title VARCHAR(100) NOT NULL COMMENT 'Notification title',
    content VARCHAR(500) NOT NULL COMMENT 'Notification content',
    related_type VARCHAR(50) NULL COMMENT 'activity, pending_review, registration',
    related_id BIGINT NULL COMMENT 'Related business id',
    read_at DATETIME NULL COMMENT 'Read time',
    created_at DATETIME NOT NULL COMMENT 'Create time',
    updated_at DATETIME NOT NULL COMMENT 'Update time',
    PRIMARY KEY (id),
    KEY idx_notifications_user_read_created (user_id, read_at, created_at),
    KEY idx_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User notifications';
