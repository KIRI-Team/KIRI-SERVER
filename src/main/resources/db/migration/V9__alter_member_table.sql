ALTER TABLE member
    ADD CONSTRAINT uk_member_nickname UNIQUE (nickname);