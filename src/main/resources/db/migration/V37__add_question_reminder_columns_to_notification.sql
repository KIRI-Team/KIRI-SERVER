ALTER TABLE notification
    ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reference_id BIGINT,
    ADD COLUMN reference_date DATE;

CREATE UNIQUE INDEX uk_notification_member_type_reference_date
    ON notification (member_id, notification_type, reference_date)
    WHERE notification_type NOT IN ('POKE', 'MISSION_CHANGED');