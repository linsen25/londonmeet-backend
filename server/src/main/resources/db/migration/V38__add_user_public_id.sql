ALTER TABLE users
    ADD COLUMN public_id VARCHAR(40) NULL AFTER id;

UPDATE users
SET public_id = CONCAT('usr_', REPLACE(UUID(), '-', ''))
WHERE public_id IS NULL OR public_id = '';

ALTER TABLE users
    MODIFY public_id VARCHAR(40) NOT NULL,
    ADD CONSTRAINT uk_users_public_id UNIQUE (public_id);
