ALTER TABLE activities
    ADD COLUMN favorite_count INT NOT NULL DEFAULT 0 AFTER liked;

CREATE TABLE activity_favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_activity_favorites_user_activity UNIQUE (user_id, activity_id),
    CONSTRAINT fk_activity_favorites_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_favorites_activity
        FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE,
    INDEX idx_activity_favorites_user_created (user_id, created_at),
    INDEX idx_activity_favorites_activity (activity_id)
);
