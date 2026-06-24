ALTER TABLE activities
    ADD COLUMN content TEXT NULL COMMENT 'Activity detail content' AFTER title,
    ADD COLUMN creator_user_id BIGINT NULL COMMENT 'Creator user id' AFTER content,
    ADD COLUMN tag_ids_json TEXT NULL COMMENT 'Selected tag ids as JSON' AFTER end_at,
    ADD COLUMN tags_json TEXT NULL COMMENT 'Selected tag names as JSON' AFTER tag_ids_json,
    ADD COLUMN recruit_count INT NULL COMMENT 'Recruitment capacity, null means unlimited' AFTER tags_json,
    ADD COLUMN location_text VARCHAR(500) NULL COMMENT 'Activity location text' AFTER recruit_count,
    ADD COLUMN map_image_url VARCHAR(500) NULL COMMENT 'Static map image URL' AFTER location_text,
    ADD COLUMN image_urls_json TEXT NULL COMMENT 'Activity image URLs as JSON' AFTER map_image_url,
    ADD COLUMN invite_qr_url VARCHAR(500) NULL COMMENT 'Optional group QR code URL' AFTER image_urls_json,
    ADD KEY idx_activities_creator_user_id (creator_user_id);
