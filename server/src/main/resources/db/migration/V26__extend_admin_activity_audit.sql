ALTER TABLE admin_audit_logs
    ADD COLUMN before_json TEXT NULL AFTER reason,
    ADD COLUMN after_json TEXT NULL AFTER before_json;

