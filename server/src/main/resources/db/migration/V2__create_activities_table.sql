CREATE TABLE activities (
                            id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Activity primary key',
                            title VARCHAR(120) NOT NULL COMMENT 'Activity title',
                            author_name VARCHAR(80) NOT NULL COMMENT 'Display name of activity creator',
                            cover_url VARCHAR(500) NOT NULL COMMENT 'Activity cover image URL',
                            avatar_url VARCHAR(500) DEFAULT NULL COMMENT 'Creator avatar URL',
                            like_count INT NOT NULL DEFAULT 0 COMMENT 'Total like count',
                            liked TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Temporary global liked state for mini program MVP',
                            start_at DATETIME NOT NULL COMMENT 'Activity start time',
                            end_at DATETIME NOT NULL COMMENT 'Activity end time',
                            progress_gif VARCHAR(500) DEFAULT NULL COMMENT 'Optional progress animation URL',
                            status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'Activity status: DRAFT, PUBLISHED, CANCELLED',
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
                            PRIMARY KEY (id),
                            KEY idx_activities_status_time (status, start_at, end_at),
                            KEY idx_activities_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Activity waterfall posts';
