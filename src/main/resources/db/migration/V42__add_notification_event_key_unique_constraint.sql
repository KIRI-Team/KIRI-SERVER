ALTER TABLE notification
    ADD CONSTRAINT uk_notification_member_event_key UNIQUE (member_id, event_key);