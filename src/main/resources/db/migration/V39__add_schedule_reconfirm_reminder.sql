ALTER TABLE schedule
    ADD COLUMN reconfirm_reminder_at TIMESTAMP;

CREATE UNIQUE INDEX uk_notification_schedule_reconfirm_reminder
    ON notification (member_id, notification_type, reference_id, reference_version)
    WHERE notification_type = 'SCHEDULE_RECONFIRM_REMINDER';