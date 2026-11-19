ALTER TABLE schedule
    ADD CONSTRAINT fk_schedule_bucket_item FOREIGN KEY (bucket_item_id) REFERENCES bucket_item (bucket_item_id);

ALTER TABLE schedule
    ADD COLUMN response_deadline TIMESTAMPTZ;

CREATE INDEX idx_schedule_bucket_item_id ON schedule (bucket_item_id);
