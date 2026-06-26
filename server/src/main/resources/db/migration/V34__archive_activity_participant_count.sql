ALTER TABLE activities
    ADD COLUMN archived_participant_count BIGINT NULL
        COMMENT 'Final approved/joined participant count retained after registration cleanup'
        AFTER recruit_count;
