ALTER TABLE schedule
    ADD COLUMN response_reminder_at TIMESTAMP,
    ADD COLUMN invite_version INTEGER NOT NULL DEFAULT 1;

ALTER TABLE notification
    ADD COLUMN reference_version INTEGER;

CREATE UNIQUE INDEX uk_notification_schedule_invitation_reminder
    ON notification (member_id, notification_type, reference_id, reference_version)
    WHERE notification_type = 'SCHEDULE_INVITATION_REMINDER';