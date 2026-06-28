ALTER TABLE users
    ADD COLUMN display_id VARCHAR(5) NULL AFTER public_id;

SET @public_user_id = 9999;

UPDATE users
SET public_id = LPAD(CAST((@public_user_id := @public_user_id + 1) AS CHAR), 5, '0')
ORDER BY RAND();

UPDATE users
SET display_id = public_id
WHERE display_id IS NULL
   OR display_id = ''
   OR display_id <> public_id;

UPDATE users
SET avatar_url = 'https://res.cloudinary.com/ddkqatprj/image/upload/v1782629106/londonmeet/defaultUser.png'
WHERE avatar_url IS NULL
   OR avatar_url = ''
   OR avatar_url LIKE 'https://dummyimage.com/300x300/%'
   OR avatar_url = '/uploads/avatar/default-avatar.png';

ALTER TABLE users
    MODIFY public_id VARCHAR(5) NOT NULL,
    MODIFY display_id VARCHAR(5) NOT NULL,
    ADD CONSTRAINT uk_users_display_id UNIQUE (display_id);

SET @seed_display_id = (
    SELECT LPAD(CAST(COALESCE(MAX(id), 0) + 10001 AS CHAR), 5, '0')
    FROM users
);

INSERT INTO users (
    public_id,
    display_id,
    openid,
    nickname,
    avatar_url,
    cover_url,
    role,
    status,
    last_login_at,
    created_at,
    updated_at
)
SELECT
    @seed_display_id,
    @seed_display_id,
    'review-seed-host',
    'Review Seed Host',
    'https://res.cloudinary.com/ddkqatprj/image/upload/v1782629106/londonmeet/defaultUser.png',
    '',
    'USER',
    'ACTIVE',
    NOW(),
    NOW(),
    NOW()
WHERE EXISTS (SELECT 1 FROM users WHERE id = 2)
  AND NOT EXISTS (SELECT 1 FROM users WHERE openid = 'review-seed-host');

SET @seed_user_id = (
    SELECT id
    FROM users
    WHERE openid = 'review-seed-host'
    LIMIT 1
);

INSERT INTO activities (
    title,
    content,
    creator_user_id,
    author_name,
    cover_url,
    avatar_url,
    favorite_count,
    start_at,
    original_start_at,
    end_at,
    tag_ids_json,
    tags_json,
    recruit_count,
    archived_participant_count,
    location_text,
    image_urls_json,
    edit_count,
    status,
    created_at,
    updated_at
)
SELECT
    seed.title,
    seed.content,
    seed.creator_user_id,
    COALESCE(NULLIF(creator.nickname, ''), 'MeetFun Host'),
    'https://dummyimage.com/1200x800/2e2e2e/ffffff.png&text=Review+Demo',
    creator.avatar_url,
    0,
    seed.start_at,
    seed.start_at,
    seed.end_at,
    '[]',
    '["Demo"]',
    12,
    1,
    seed.location_text,
    '[]',
    0,
    'PUBLISHED',
    NOW(),
    NOW()
FROM (
    SELECT
        'Review demo: Coffee Walk' AS title,
        'Seed activity for testing pending activity reviews.' AS content,
        @seed_user_id AS creator_user_id,
        DATE_SUB(NOW(), INTERVAL 2 DAY) AS start_at,
        DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 2 HOUR AS end_at,
        'London' AS location_text
    UNION ALL
    SELECT
        'Review demo: Host Workshop',
        'Seed activity for testing pending member reviews.',
        2,
        DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 2 HOUR,
        'London'
) seed
JOIN users creator ON creator.id = seed.creator_user_id
WHERE @seed_user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM users WHERE id = 2)
  AND NOT EXISTS (
      SELECT 1
      FROM activities existing
      WHERE existing.title = seed.title
        AND existing.creator_user_id = seed.creator_user_id
  );

INSERT INTO activity_registrations (
    user_id,
    activity_id,
    status,
    notice_code,
    application_text,
    reviewed_by,
    reviewed_at,
    approved_at,
    joined_group_at,
    created_at,
    updated_at
)
SELECT
    registration.user_id,
    a.id,
    'joined_group',
    1005,
    'Seed registration for pending review testing.',
    a.creator_user_id,
    DATE_SUB(a.start_at, INTERVAL 1 DAY),
    DATE_SUB(a.start_at, INTERVAL 1 DAY),
    a.end_at,
    DATE_SUB(a.start_at, INTERVAL 1 DAY),
    NOW()
FROM (
    SELECT 'Review demo: Coffee Walk' AS title, 2 AS user_id
    UNION ALL
    SELECT 'Review demo: Host Workshop', @seed_user_id
) registration
JOIN activities a ON a.title = registration.title
WHERE @seed_user_id IS NOT NULL
  AND registration.user_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM users WHERE id = 2)
  AND NOT EXISTS (
      SELECT 1
      FROM activity_registrations r
      WHERE r.user_id = registration.user_id
        AND r.activity_id = a.id
  );
