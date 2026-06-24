CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_settings (setting_key, setting_value)
VALUES ('activity_detail_retention_days', '30')
ON DUPLICATE KEY UPDATE setting_value = setting_value;

CREATE TABLE IF NOT EXISTS monthly_activity_reports (
    month_start DATE PRIMARY KEY,
    archived_activity_count BIGINT NOT NULL DEFAULT 0,
    archived_registration_count BIGINT NOT NULL DEFAULT 0,
    archived_joined_count BIGINT NOT NULL DEFAULT 0,
    archived_report_count BIGINT NOT NULL DEFAULT 0,
    archived_favorite_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
