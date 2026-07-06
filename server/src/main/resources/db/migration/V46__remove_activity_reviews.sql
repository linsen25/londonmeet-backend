DELETE FROM notifications
WHERE type IN ('review_reminder', 'review_available', 'review_received', 'review_moderated')
   OR related_type = 'pending_review';

DROP TABLE IF EXISTS activity_reviews;
