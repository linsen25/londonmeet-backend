ALTER TABLE activities
    ADD COLUMN original_start_at DATETIME NULL AFTER start_at,
    ADD COLUMN edit_count INT NOT NULL DEFAULT 0 AFTER invite_qr_url,
    ADD COLUMN qr_expires_at DATETIME NULL AFTER edit_count,
    ADD COLUMN qr_reminder_sent_at DATETIME NULL AFTER qr_expires_at;

UPDATE activities
SET original_start_at = start_at
WHERE original_start_at IS NULL;

