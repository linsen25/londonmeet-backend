CREATE TEMPORARY TABLE tmp_random_user_ids (
    row_num INT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    public_id VARCHAR(5) NOT NULL UNIQUE
);

ALTER TABLE users
    DROP INDEX uk_users_public_id,
    DROP INDEX uk_users_display_id;

SET @user_row = 0;
SET @number_row = 0;

INSERT INTO tmp_random_user_ids (row_num, user_id, public_id)
SELECT ranked_users.row_num, ranked_users.user_id, ranked_numbers.public_id
FROM (
    SELECT (@user_row := @user_row + 1) AS row_num, ordered_users.id AS user_id
    FROM (
        SELECT id
        FROM users
        ORDER BY RAND()
    ) ordered_users
) ranked_users
JOIN (
    SELECT (@number_row := @number_row + 1) AS row_num, pool.public_id
    FROM (
        SELECT LPAD(CAST(10000 + ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 + ten_thousands.n * 10000 AS CHAR), 5, '0') AS public_id
        FROM (
            SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        ) ones
        CROSS JOIN (
            SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        ) tens
        CROSS JOIN (
            SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        ) hundreds
        CROSS JOIN (
            SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
        ) thousands
        CROSS JOIN (
            SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
        ) ten_thousands
        ORDER BY RAND()
    ) pool
) ranked_numbers ON ranked_numbers.row_num = ranked_users.row_num;

UPDATE users u
JOIN tmp_random_user_ids random_ids ON random_ids.user_id = u.id
SET u.public_id = random_ids.public_id,
    u.display_id = random_ids.public_id;

DROP TEMPORARY TABLE tmp_random_user_ids;

ALTER TABLE users
    ADD CONSTRAINT uk_users_public_id UNIQUE (public_id),
    ADD CONSTRAINT uk_users_display_id UNIQUE (display_id);

UPDATE users
SET nickname = '待评价测试用户'
WHERE openid = 'review-seed-host';

UPDATE activities
SET title = '待评价测试：咖啡散步',
    content = '用于测试待评价活动的数据。',
    author_name = COALESCE(NULLIF(author_name, ''), '活动发起人'),
    tags_json = '["测试"]',
    location_text = '伦敦'
WHERE title = 'Review demo: Coffee Walk';

UPDATE activities
SET title = '待评价测试：主理人工作坊',
    content = '用于测试待评价成员的数据。',
    author_name = COALESCE(NULLIF(author_name, ''), '活动发起人'),
    tags_json = '["测试"]',
    location_text = '伦敦'
WHERE title = 'Review demo: Host Workshop';

UPDATE activity_registrations r
JOIN activities a ON a.id = r.activity_id
SET r.application_text = '用于测试待评价的数据。'
WHERE a.title IN ('待评价测试：咖啡散步', '待评价测试：主理人工作坊');
