
ALTER TABLE member
    DROP COLUMN gender,
    DROP COLUMN birth_date;


ALTER TABLE member
    ADD CONSTRAINT uk_member_social UNIQUE (social_provider, social_provider_uid);