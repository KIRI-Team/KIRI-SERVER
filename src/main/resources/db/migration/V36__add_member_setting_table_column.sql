ALTER TABLE member_setting ADD COLUMN is_all_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE member_setting ADD COLUMN regular_notification_time TIME NOT NULL DEFAULT '20:00:00';