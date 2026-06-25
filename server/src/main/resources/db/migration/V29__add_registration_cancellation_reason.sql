ALTER TABLE activity_registrations
    ADD COLUMN cancellation_reason_type VARCHAR(30) NULL AFTER application_text,
    ADD COLUMN cancellation_reason_text VARCHAR(100) NULL AFTER cancellation_reason_type,
    ADD COLUMN cancelled_at DATETIME NULL AFTER joined_group_at;

