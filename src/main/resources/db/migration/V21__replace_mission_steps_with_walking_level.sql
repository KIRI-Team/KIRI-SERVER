ALTER TABLE mission
    DROP COLUMN estimated_steps_min,
    DROP COLUMN estimated_steps_max,
    ADD COLUMN walking_level VARCHAR(20);
