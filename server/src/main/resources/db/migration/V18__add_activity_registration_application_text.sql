ALTER TABLE activity_registrations
    ADD COLUMN application_text VARCHAR(100) NULL COMMENT 'Optional applicant message for review' AFTER notice_code;
