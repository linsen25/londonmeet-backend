DELETE r
FROM activity_registrations r
JOIN activities a ON a.id = r.activity_id
WHERE a.title IN (
    'Capacity Demo Green 3/14',
    'Capacity Demo Orange 10/14',
    'Capacity Demo Red 13/14',
    'Capacity Demo Full 14/14'
);

DELETE FROM activities
WHERE title IN (
    'Capacity Demo Green 3/14',
    'Capacity Demo Orange 10/14',
    'Capacity Demo Red 13/14',
    'Capacity Demo Full 14/14'
);

INSERT IGNORE INTO users (openid, nickname, avatar_url, role, status)
VALUES
    ('capacity-demo-user-01', 'Demo User 01', 'https://picsum.photos/seed/capacity-demo-user-01/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-02', 'Demo User 02', 'https://picsum.photos/seed/capacity-demo-user-02/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-03', 'Demo User 03', 'https://picsum.photos/seed/capacity-demo-user-03/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-04', 'Demo User 04', 'https://picsum.photos/seed/capacity-demo-user-04/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-05', 'Demo User 05', 'https://picsum.photos/seed/capacity-demo-user-05/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-06', 'Demo User 06', 'https://picsum.photos/seed/capacity-demo-user-06/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-07', 'Demo User 07', 'https://picsum.photos/seed/capacity-demo-user-07/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-08', 'Demo User 08', 'https://picsum.photos/seed/capacity-demo-user-08/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-09', 'Demo User 09', 'https://picsum.photos/seed/capacity-demo-user-09/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-10', 'Demo User 10', 'https://picsum.photos/seed/capacity-demo-user-10/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-11', 'Demo User 11', 'https://picsum.photos/seed/capacity-demo-user-11/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-12', 'Demo User 12', 'https://picsum.photos/seed/capacity-demo-user-12/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-13', 'Demo User 13', 'https://picsum.photos/seed/capacity-demo-user-13/100/100', 'USER', 'ACTIVE'),
    ('capacity-demo-user-14', 'Demo User 14', 'https://picsum.photos/seed/capacity-demo-user-14/100/100', 'USER', 'ACTIVE');

INSERT INTO activities
    (
        title,
        content,
        creator_user_id,
        author_name,
        cover_url,
        avatar_url,
        like_count,
        liked,
        favorite_count,
        start_at,
        end_at,
        tag_id,
        tag_ids_json,
        tags_json,
        recruit_count,
        location_text,
        map_image_url,
        image_urls_json,
        invite_qr_url,
        status
    )
VALUES
    (
        'Capacity Demo Green 3/14',
        'Capacity progress demo: normal registration state.',
        1,
        'MeetFun',
        'https://picsum.photos/seed/capacity-demo-green/600/800',
        'https://picsum.photos/seed/capacity-demo-avatar-green/100/100',
        12,
        0,
        0,
        DATE_ADD(NOW(), INTERVAL 2 HOUR),
        DATE_ADD(NOW(), INTERVAL 20 HOUR),
        6,
        '[6]',
        '["Social"]',
        14,
        'South Bank, London SE1',
        'https://picsum.photos/seed/capacity-demo-map-green/800/360',
        '["https://picsum.photos/seed/capacity-demo-green/600/800"]',
        'https://api.qrserver.com/v1/create-qr-code/?size=440x440&data=capacity-demo-green',
        'PUBLISHED'
    ),
    (
        'Capacity Demo Orange 10/14',
        'Capacity progress demo: almost full registration state.',
        1,
        'MeetFun',
        'https://picsum.photos/seed/capacity-demo-orange/600/800',
        'https://picsum.photos/seed/capacity-demo-avatar-orange/100/100',
        18,
        0,
        0,
        DATE_ADD(NOW(), INTERVAL 3 HOUR),
        DATE_ADD(NOW(), INTERVAL 21 HOUR),
        6,
        '[6]',
        '["Social"]',
        14,
        'Waterloo, London SE1',
        'https://picsum.photos/seed/capacity-demo-map-orange/800/360',
        '["https://picsum.photos/seed/capacity-demo-orange/600/800"]',
        'https://api.qrserver.com/v1/create-qr-code/?size=440x440&data=capacity-demo-orange',
        'PUBLISHED'
    ),
    (
        'Capacity Demo Red 13/14',
        'Capacity progress demo: urgent registration state.',
        1,
        'MeetFun',
        'https://picsum.photos/seed/capacity-demo-red/600/800',
        'https://picsum.photos/seed/capacity-demo-avatar-red/100/100',
        24,
        0,
        0,
        DATE_ADD(NOW(), INTERVAL 4 HOUR),
        DATE_ADD(NOW(), INTERVAL 22 HOUR),
        6,
        '[6]',
        '["Social"]',
        14,
        'Shoreditch High Street, London E1',
        'https://picsum.photos/seed/capacity-demo-map-red/800/360',
        '["https://picsum.photos/seed/capacity-demo-red/600/800"]',
        'https://api.qrserver.com/v1/create-qr-code/?size=440x440&data=capacity-demo-red',
        'PUBLISHED'
    ),
    (
        'Capacity Demo Full 14/14',
        'Capacity progress demo: full registration state.',
        1,
        'MeetFun',
        'https://picsum.photos/seed/capacity-demo-full/600/800',
        'https://picsum.photos/seed/capacity-demo-avatar-full/100/100',
        30,
        0,
        0,
        DATE_ADD(NOW(), INTERVAL 5 HOUR),
        DATE_ADD(NOW(), INTERVAL 23 HOUR),
        6,
        '[6]',
        '["Social"]',
        14,
        'Hyde Park Corner, London W1J',
        'https://picsum.photos/seed/capacity-demo-map-full/800/360',
        '["https://picsum.photos/seed/capacity-demo-full/600/800"]',
        'https://api.qrserver.com/v1/create-qr-code/?size=440x440&data=capacity-demo-full',
        'PUBLISHED'
    );

INSERT INTO activity_registrations (user_id, activity_id, status, notice_code, reviewed_at)
SELECT u.id, a.id, 'approved', 1004, NOW()
FROM users u
JOIN activities a ON a.title = 'Capacity Demo Green 3/14'
WHERE u.openid IN ('capacity-demo-user-01', 'capacity-demo-user-02', 'capacity-demo-user-03');

INSERT INTO activity_registrations (user_id, activity_id, status, notice_code, reviewed_at)
SELECT u.id, a.id, 'approved', 1004, NOW()
FROM users u
JOIN activities a ON a.title = 'Capacity Demo Orange 10/14'
WHERE u.openid IN (
    'capacity-demo-user-01',
    'capacity-demo-user-02',
    'capacity-demo-user-03',
    'capacity-demo-user-04',
    'capacity-demo-user-05',
    'capacity-demo-user-06',
    'capacity-demo-user-07',
    'capacity-demo-user-08',
    'capacity-demo-user-09',
    'capacity-demo-user-10'
);

INSERT INTO activity_registrations (user_id, activity_id, status, notice_code, reviewed_at)
SELECT u.id, a.id, 'approved', 1004, NOW()
FROM users u
JOIN activities a ON a.title = 'Capacity Demo Red 13/14'
WHERE u.openid IN (
    'capacity-demo-user-01',
    'capacity-demo-user-02',
    'capacity-demo-user-03',
    'capacity-demo-user-04',
    'capacity-demo-user-05',
    'capacity-demo-user-06',
    'capacity-demo-user-07',
    'capacity-demo-user-08',
    'capacity-demo-user-09',
    'capacity-demo-user-10',
    'capacity-demo-user-11',
    'capacity-demo-user-12',
    'capacity-demo-user-13'
);

INSERT INTO activity_registrations (user_id, activity_id, status, notice_code, reviewed_at)
SELECT u.id, a.id, 'approved', 1004, NOW()
FROM users u
JOIN activities a ON a.title = 'Capacity Demo Full 14/14'
WHERE u.openid IN (
    'capacity-demo-user-01',
    'capacity-demo-user-02',
    'capacity-demo-user-03',
    'capacity-demo-user-04',
    'capacity-demo-user-05',
    'capacity-demo-user-06',
    'capacity-demo-user-07',
    'capacity-demo-user-08',
    'capacity-demo-user-09',
    'capacity-demo-user-10',
    'capacity-demo-user-11',
    'capacity-demo-user-12',
    'capacity-demo-user-13',
    'capacity-demo-user-14'
);
