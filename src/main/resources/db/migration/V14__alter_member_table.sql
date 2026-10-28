ALTER TABLE member
    DROP COLUMN last_access_at;

ALTER TABLE member
    ADD COLUMN deleted_at TIMESTAMP NULL;

ALTER TABLE member
    ADD COLUMN family_joined_at TIMESTAMP NULL;