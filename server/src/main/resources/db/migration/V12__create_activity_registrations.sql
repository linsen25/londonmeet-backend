ALTER TABLE activities
    ADD COLUMN tag_id BIGINT NULL COMMENT 'Single activity tag id' AFTER end_at,
    ADD KEY idx_activities_tag_id (tag_id);

UPDATE activities
SET tag_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(tag_ids_json, '$[0]')) AS UNSIGNED)
WHERE tag_id IS NULL
  AND tag_ids_json IS NOT NULL
  AND JSON_VALID(tag_ids_json)
  AND JSON_EXTRACT(tag_ids_json, '$[0]') IS NOT NULL;

CREATE TABLE activity_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Registration primary key',
    user_id BIGINT NOT NULL COMMENT 'Applicant user id',
    activity_id BIGINT NOT NULL COMMENT 'Activity id',
    status VARCHAR(30) NOT NULL DEFAULT 'pending' COMMENT 'pending, approved, joined_group, rejected, cancelled',
    notice_code INT NOT NULL DEFAULT 1001 COMMENT 'Notification/reason code',
    reviewed_by BIGINT NULL COMMENT 'Reviewer user id',
    reviewed_at DATETIME NULL COMMENT 'Review time',
    joined_group_at DATETIME NULL COMMENT 'Group QR opened time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_registrations_user_activity (user_id, activity_id),
    KEY idx_activity_registrations_activity_status (activity_id, status),
    KEY idx_activity_registrations_user_status (user_id, status),
    CONSTRAINT fk_activity_registrations_activity
        FOREIGN KEY (activity_id) REFERENCES activities(id),
    CONSTRAINT fk_activity_registrations_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User activity registration status';
