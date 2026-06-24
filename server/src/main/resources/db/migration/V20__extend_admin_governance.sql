ALTER TABLE activity_reports
    ADD COLUMN admin_note VARCHAR(500) NULL AFTER status,
    ADD COLUMN handled_by BIGINT NULL AFTER admin_note,
    ADD COLUMN handled_at DATETIME NULL AFTER handled_by;

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_audit_target (target_type, target_id),
    INDEX idx_admin_audit_admin (admin_user_id)
);
