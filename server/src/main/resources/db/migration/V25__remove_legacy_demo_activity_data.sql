-- One-time cleanup for demo records inserted by migrations before activity seeding
-- was removed. Match only the asset URLs dedicated to those demo records so real
-- user-created activities are preserved.

CREATE TEMPORARY TABLE legacy_demo_activity_ids (
    id BIGINT PRIMARY KEY
);

INSERT INTO legacy_demo_activity_ids (id)
SELECT id
FROM activities
WHERE cover_url LIKE 'https://dummyimage.com/600x800/%'
   OR cover_url LIKE 'https://picsum.photos/seed/meetfun-%'
   OR cover_url LIKE 'https://picsum.photos/seed/capacity-demo-%';

DELETE n
FROM notifications n
JOIN legacy_demo_activity_ids d
  ON n.related_type = 'activity' AND n.related_id = d.id;

DELETE r
FROM activity_reports r
JOIN legacy_demo_activity_ids d ON r.activity_id = d.id;

DELETE f
FROM activity_favorites f
JOIN legacy_demo_activity_ids d ON f.activity_id = d.id;

DELETE r
FROM activity_reviews r
JOIN legacy_demo_activity_ids d ON r.activity_id = d.id;

DELETE r
FROM activity_registrations r
JOIN legacy_demo_activity_ids d ON r.activity_id = d.id;

DELETE a
FROM activities a
JOIN legacy_demo_activity_ids d ON a.id = d.id;

DELETE FROM users
WHERE openid LIKE 'capacity-demo-user-%';

DROP TEMPORARY TABLE legacy_demo_activity_ids;
