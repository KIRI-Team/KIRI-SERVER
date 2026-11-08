ALTER TABLE family_mission_recommendation
    ADD COLUMN started_at TIMESTAMP NOT NULL,
    ADD COLUMN ended_at TIMESTAMP NOT NULL,
    ADD COLUMN completed_at TIMESTAMP;

ALTER TABLE family_mission_recommendation
    DROP CONSTRAINT IF EXISTS uk_family_mission_recommendation;

ALTER TABLE family_mission_recommendation
    ADD CONSTRAINT uk_family_mission_recommendation_period UNIQUE (family_id, started_at);