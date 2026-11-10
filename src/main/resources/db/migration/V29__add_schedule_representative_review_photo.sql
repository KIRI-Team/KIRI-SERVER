ALTER TABLE schedule
    ADD COLUMN representative_review_photo_id BIGINT NULL;

ALTER TABLE schedule
    ADD CONSTRAINT fk_schedule_representative_review_photo
        FOREIGN KEY (representative_review_photo_id) REFERENCES review_photo (review_photo_id) ON DELETE SET NULL;
