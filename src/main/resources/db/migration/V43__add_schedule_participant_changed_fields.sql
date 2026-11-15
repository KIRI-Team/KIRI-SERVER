-- 재확인이 필요한 참여자에게 무엇이 바뀌었는지 보여주기 위한 컬럼. 응답하면 FALSE로 되돌린다.
ALTER TABLE schedule_participant
    ADD COLUMN is_date_changed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_time_changed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_participants_changed BOOLEAN NOT NULL DEFAULT FALSE;
