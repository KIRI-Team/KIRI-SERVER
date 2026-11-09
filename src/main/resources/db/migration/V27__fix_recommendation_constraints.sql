ALTER TABLE member_answer_category
    ALTER COLUMN updated_at DROP NOT NULL;

ALTER TABLE family_mission_recommendation
    ALTER COLUMN updated_at DROP NOT NULL;

ALTER TABLE characters
    ALTER COLUMN updated_at DROP NOT NULL;

-- V25에서 제약 이름이 달라 삭제되지 않은 기존 유니크 제약 제거
ALTER TABLE family_mission_recommendation
    DROP CONSTRAINT uk_family_mission_recommendation_family_mission;
