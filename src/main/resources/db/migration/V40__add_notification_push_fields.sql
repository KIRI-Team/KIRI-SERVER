ALTER TABLE notification
    ADD COLUMN priority VARCHAR(2) NOT NULL DEFAULT 'P5',
    ADD COLUMN scheduled_at TIMESTAMPTZ,
    ADD COLUMN event_key VARCHAR(200);

ALTER TABLE notification
    ALTER COLUMN priority DROP DEFAULT;