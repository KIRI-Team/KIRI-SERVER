ALTER TABLE member
    ADD COLUMN refresh_token_hash VARCHAR(64);

ALTER TABLE member
    ADD CONSTRAINT uk_member_nickname UNIQUE (nickname);

ALTER TABLE member
    ADD CONSTRAINT fk_member_family FOREIGN KEY (family_id) REFERENCES family (family_id);