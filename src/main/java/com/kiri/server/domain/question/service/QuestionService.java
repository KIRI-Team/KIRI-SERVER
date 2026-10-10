package com.kiri.server.domain.question.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.converter.QuestionConverter;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QuestionService {

    // 서버 시간대 설정과 무관하게 한국 자정 기준으로 "오늘"을 판단
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final FamilyQuestionRepository familyQuestionRepository;
    private final MemberAnswerRepository memberAnswerRepository;

    @Transactional(readOnly = true)
    public QuestionResDTO.TodayQuestion getTodayQuestion(Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        // 가족이 없어도 홈에 진입할 수 있으므로 에러가 아닌 정상 응답으로 처리
        Family family = member.getFamily();
        if (family == null) {
            return QuestionConverter.toNoFamily();
        }

        LocalDate today = LocalDate.now(KST);

        FamilyQuestion familyQuestion = familyQuestionRepository.findByFamilyAndQuestionDate(family, today)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.TODAY_QUESTION_NOT_FOUND));

        // 답변 행이 있으면 답변한 것 (content NOT NULL)
        Optional<MemberAnswer> answer = memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member);

        return QuestionConverter.toTodayQuestion(familyQuestion, answer);
    }
}
