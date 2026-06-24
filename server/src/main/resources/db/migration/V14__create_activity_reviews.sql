CREATE TABLE activity_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Review primary key',
    reviewer_user_id BIGINT NOT NULL COMMENT 'Reviewer user id',
    activity_id BIGINT NOT NULL COMMENT 'Activity id',
    target_type VARCHAR(20) NOT NULL COMMENT 'activity or member',
    target_id BIGINT NOT NULL DEFAULT 0 COMMENT '0 for activity, user id for member',
    overall_score DECIMAL(3,2) NOT NULL COMMENT 'Average score, max 5',
    scores_json TEXT NOT NULL COMMENT 'Dimension scores json',
    created_at DATETIME NOT NULL COMMENT 'Create time',
    updated_at DATETIME NOT NULL COMMENT 'Update time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_reviews_once (reviewer_user_id, activity_id, target_type, target_id),
    KEY idx_activity_reviews_activity_target (activity_id, target_type, target_id),
    KEY idx_activity_reviews_target (target_type, target_id),
    CONSTRAINT fk_activity_reviews_activity
        FOREIGN KEY (activity_id) REFERENCES activities(id),
    CONSTRAINT fk_activity_reviews_reviewer
        FOREIGN KEY (reviewer_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Activity and member reviews';
