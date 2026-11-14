ALTER TABLE notification
    ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reference_id BIGINT,
    ADD COLUMN reference_date DATE;

ALTER TABLE notification
    ADD CONSTRAINT uk_notification_member_type_reference_date UNIQUE (member_id, notification_type, reference_date);