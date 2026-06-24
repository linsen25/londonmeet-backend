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

INSERT INTO activities
    (title, author_name, cover_url, avatar_url, like_count, liked, start_at, end_at, status)
VALUES
    (
        'London Board Game Night',
        'Alice',
        'https://dummyimage.com/600x800/2f6f73/ffffff.png&text=Board+Game',
        'https://dummyimage.com/100x100/2f6f73/ffffff.png&text=A',
        12,
        0,
        DATE_SUB(NOW(), INTERVAL 2 HOUR),
        DATE_ADD(NOW(), INTERVAL 6 HOUR),
        'PUBLISHED'
    ),
    (
        'Hyde Park Picnic',
        'Ben',
        'https://dummyimage.com/600x800/6b8e4e/ffffff.png&text=Picnic',
        'https://dummyimage.com/100x100/6b8e4e/ffffff.png&text=B',
        34,
        0,
        DATE_ADD(NOW(), INTERVAL 1 DAY),
        DATE_ADD(NOW(), INTERVAL 2 DAY),
        'PUBLISHED'
    ),
    (
        'Tech Meetup London',
        'Cindy',
        'https://dummyimage.com/600x800/4a5d8f/ffffff.png&text=Tech',
        'https://dummyimage.com/100x100/4a5d8f/ffffff.png&text=C',
        56,
        0,
        DATE_ADD(NOW(), INTERVAL 3 DAY),
        DATE_ADD(NOW(), INTERVAL 3 DAY) + INTERVAL 4 HOUR,
        'PUBLISHED'
    ),
    (
        'Weekend Coffee Walk',
        'David',
        'https://dummyimage.com/600x800/8c6d46/ffffff.png&text=Coffee',
        'https://dummyimage.com/100x100/8c6d46/ffffff.png&text=D',
        21,
        0,
        DATE_ADD(NOW(), INTERVAL 10 DAY),
        DATE_ADD(NOW(), INTERVAL 10 DAY) + INTERVAL 3 HOUR,
        'PUBLISHED'
    );
