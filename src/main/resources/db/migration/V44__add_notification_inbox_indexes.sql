-- 알림함 조회(회원별 최신순)·미읽음 수·보관기간 정리용 인덱스
CREATE INDEX IF NOT EXISTS idx_notification_member_created
    ON notification (member_id, created_at DESC, notification_id DESC);

CREATE INDEX IF NOT EXISTS idx_notification_member_unread
    ON notification (member_id, created_at)
    WHERE is_read = FALSE;

CREATE INDEX IF NOT EXISTS idx_notification_created_at
    ON notification (created_at);

-- V37의 (회원, 알림 종류, 날짜) 유니크 인덱스는 일정 알림까지 묶어서
-- 같은 일정의 두 번째 응답·변경 알림이나 같은 날짜의 다른 일정 알림을 중복으로 막았다.
-- 날짜당 1건이 맞는 질문·주간 미션 알림에만 걸리도록 범위를 좁힌다.
DROP INDEX IF EXISTS uk_notification_member_type_reference_date;

CREATE UNIQUE INDEX uk_notification_member_type_reference_date
    ON notification (member_id, notification_type, reference_date)
    WHERE notification_type IN ('QUESTION_REMINDER', 'QUESTION_COMPLETE', 'MISSION');
