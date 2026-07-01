ALTER TABLE activity_registrations
    ADD COLUMN review_reason_type VARCHAR(30) NULL AFTER cancellation_reason_text,
    ADD COLUMN review_reason_text VARCHAR(200) NULL AFTER review_reason_type;

CREATE TABLE activity_organizer_blacklist (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Blacklist primary key',
    organizer_user_id BIGINT NOT NULL COMMENT 'Organizer user id',
    blocked_user_id BIGINT NOT NULL COMMENT 'Blocked user id',
    reason_type VARCHAR(30) NOT NULL COMMENT 'Blacklist reason type',
    reason_text VARCHAR(200) NULL COMMENT 'Blacklist reason text',
    active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether the blacklist entry is active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    unblocked_at DATETIME NULL COMMENT 'Unblocked time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_blacklist_organizer_user (organizer_user_id, blocked_user_id),
    KEY idx_activity_blacklist_blocked_active (blocked_user_id, active),
    CONSTRAINT fk_activity_blacklist_organizer
        FOREIGN KEY (organizer_user_id) REFERENCES users(id),
    CONSTRAINT fk_activity_blacklist_blocked_user
        FOREIGN KEY (blocked_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Organizer-level activity registration blacklist';
