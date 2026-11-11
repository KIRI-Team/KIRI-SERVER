-- 관리자 수집 중단 스위치. 스케줄러와 수동 실행 모두 이 값을 보고 건너뛴다.
ALTER TABLE external_sync_state ADD COLUMN admin_paused BOOLEAN NOT NULL DEFAULT FALSE;
