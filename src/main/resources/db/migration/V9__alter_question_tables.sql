-- 질문 풀이 소진되면 같은 질문을 다시 배정해야 하므로 가족별 질문 중복 제약 제거
ALTER TABLE family_question DROP CONSTRAINT uk_family_question_question;

-- 질문이 수집하는 취향 종류 (NULL이면 일반 질문)
ALTER TABLE question ADD COLUMN taste_type VARCHAR(20);
