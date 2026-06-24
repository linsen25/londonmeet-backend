ALTER TABLE users
    ADD COLUMN cover_url VARCHAR(500) DEFAULT 'https://dummyimage.com/1200x800/2b2b2b/ffffff.png&text=Cover' COMMENT 'User profile cover image URL' AFTER avatar_url,
    ADD COLUMN motto VARCHAR(100) DEFAULT NULL COMMENT 'User profile motto' AFTER cover_url,
    ADD COLUMN tags_json TEXT DEFAULT NULL COMMENT 'User profile tags JSON' AFTER motto;

UPDATE users
SET cover_url = 'https://dummyimage.com/1200x800/2b2b2b/ffffff.png&text=Cover'
WHERE cover_url IS NULL OR cover_url = '';
