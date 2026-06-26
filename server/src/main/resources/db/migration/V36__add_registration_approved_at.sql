ALTER TABLE activity_registrations
    ADD COLUMN approved_at DATETIME NULL AFTER reviewed_at,
    ADD KEY idx_activity_registrations_approved_at (approved_at);

UPDATE activity_registrations
SET approved_at = reviewed_at
WHERE approved_at IS NULL
  AND status IN ('approved', 'joined_group')
  AND reviewed_at IS NOT NULL;
