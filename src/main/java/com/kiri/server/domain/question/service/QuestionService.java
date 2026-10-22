package com.kiri.server.domain.question.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.converter.QuestionConverter;
import com.kiri.server.domain.question.dto.QuestionReqDTO;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.FamilyQuestionRepository;
import com.kiri.server.domain.question.repository.MemberAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final MemberRepository memberRepository;
    private final FamilyQuestionRepository familyQuestionRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public QuestionResDTO.TodayQuestion getTodayQuestion(Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        // 가족이 없어도 홈에 진입할 수 있으므로 에러가 아닌 정상 응답으로 처리
        Family family = member.getFamily();
        if (family == null) {
            return QuestionConverter.toNoFamily();
        }

        LocalDate today = LocalDate.now(clock);

        FamilyQuestion familyQuestion = familyQuestionRepository.findByFamilyAndQuestionDate(family, today)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.TODAY_QUESTION_NOT_FOUND));

        // 답변 행이 있으면 답변한 것 (content NOT NULL)
        Optional<MemberAnswer> answer = memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member);

        return QuestionConverter.toTodayQuestion(familyQuestion, answer);
    }

    @Transactional
    public void writeAnswer(Long memberId, Long questionId, QuestionReqDTO.WriteAnswer request) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        FamilyQuestion familyQuestion = familyQuestionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND));

        // 내 가족에게 배정된 질문인지 확인
        Family family = member.getFamily();
        if (family == null || !family.getId().equals(familyQuestion.getFamily().getId())) {
            throw new QuestionException(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
        }

        // 답변 작성/수정은 질문이 배정된 당일에만 가능
        if (!familyQuestion.getQuestionDate().equals(LocalDate.now(clock))) {
            throw new QuestionException(QuestionErrorCode.ANSWER_PERIOD_EXPIRED);
        }

        Optional<MemberAnswer> answer = memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member);
        if (answer.isPresent()) {
            answer.get().updateContent(request.answer());
            return;
        }

        // 동시에 두 번 생성 요청이 오면 uk_member_answer 유니크 제약이 막으므로 409로 변환
        try {
            memberAnswerRepository.save(QuestionConverter.toMemberAnswer(request.answer(), familyQuestion, member));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateAnswer(e)) {
                throw new QuestionException(QuestionErrorCode.DUPLICATE_ANSWER_REQUEST);
            }
            throw e;
        }
    }

    // 유니크 제약 여부 확인
    private boolean isDuplicateAnswer(DataIntegrityViolationException e) {
        if (!(e.getMostSpecificCause() instanceof PSQLException psqlException)) {
            return false;
        }
        ServerErrorMessage serverError = psqlException.getServerErrorMessage();
        return serverError != null && "uk_member_answer".equals(serverError.getConstraint());
    }
}
