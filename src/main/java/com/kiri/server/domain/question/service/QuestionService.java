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
import com.kiri.server.domain.question.repository.PokeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final MemberRepository memberRepository;
    private final FamilyQuestionRepository familyQuestionRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final Clock clock;
    private final PokeRepository pokeRepository;

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

        FamilyQuestion familyQuestion = getMyFamilyQuestion(member, questionId);

        // 답변 작성/수정은 질문이 배정된 당일에만 가능
        if (!familyQuestion.getQuestionDate().equals(LocalDate.now(clock))) {
            throw new QuestionException(QuestionErrorCode.ANSWER_PERIOD_EXPIRED);
        }

        // 동시에 두 번 생성 요청이 오면 uk_member_answer 유니크 제약이 막고 전역 핸들러가 409로 응답
        memberAnswerRepository.findByFamilyQuestionAndMember(familyQuestion, member)
                .ifPresentOrElse(
                        answer -> answer.updateContent(request.answer()),
                        () -> memberAnswerRepository.save(
                                QuestionConverter.toMemberAnswer(request.answer(), familyQuestion, member)
                        )
                );
    }

    @Transactional(readOnly = true)
    public QuestionResDTO.FamilyAnswers getFamilyAnswers(Long memberId, Long questionId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        FamilyQuestion familyQuestion = getMyFamilyQuestion(member, questionId);

        // 미리 배정된 미래 질문은 아직 공개되지 않았으므로 존재하지 않는 것처럼 응답
        if (familyQuestion.getQuestionDate().isAfter(LocalDate.now(clock))) {
            throw new QuestionException(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
        }

        // 가족 조회 (나를 맨 앞에 두고, 나머지는 FamilyRole 선언 순서대로)
        List<Member> familyMembers = memberRepository.findAllByFamily(member.getFamily()).stream()
                .sorted(Comparator.comparing((Member m) ->
                                !m.getId().equals(memberId))
                        .thenComparing(Member::getFamilyRole, Comparator.naturalOrder()))
                .toList();

        // 가족 답변 조회
        Map<Long, MemberAnswer> answerByMemberId = memberAnswerRepository.findAllByFamilyQuestion(familyQuestion).stream()
                .collect(Collectors.toMap(answer -> answer.getMember().getId(), Function.identity()));

        return QuestionConverter.toFamilyAnswers(familyQuestion, familyMembers, answerByMemberId, memberId);
    }

    @Transactional
    public void poke(Long memberId, Long targetId) {

        // 자기 자신은 찌를 수 없음
        if (memberId.equals(targetId)) {
            throw new QuestionException(QuestionErrorCode.SELF_POKE);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));

        // 요청자는 반드시 가족에 소속되어 있어야 함
        Family family = member.getFamily();
        if (family == null) {
            throw new QuestionException(QuestionErrorCode.FAMILY_REQUIRED);
        }

        // 대상이 없는 회원
        Member target = memberRepository.findById(targetId)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.POKE_TARGET_NOT_FOUND));

        // 대상이 다른 가족
        Family targetFamily = target.getFamily();
        if (targetFamily == null || !family.getId().equals(targetFamily.getId())) {
            throw new QuestionException(QuestionErrorCode.POKE_TARGET_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);

        // 같은 사람에게 하루 한 번만 가능
        if (pokeRepository.existsBySenderAndReceiverAndPokeDate(member, target, today)) {
            throw new QuestionException(QuestionErrorCode.ALREADY_POKED);
        }

        try {
            pokeRepository.save(QuestionConverter.toPoke(member, target, today));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicatePoke(e)) {
                throw new QuestionException(QuestionErrorCode.ALREADY_POKED);
            }
            throw e;
        }
    }

    // 내 가족에게 배정된 질문만 조회 (다른 가족의 질문은 존재 여부를 숨기기 위해 같은 404)
    private FamilyQuestion getMyFamilyQuestion(Member member, Long questionId) {
        FamilyQuestion familyQuestion = familyQuestionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND));

        Family family = member.getFamily();
        if (family == null || !family.getId().equals(familyQuestion.getFamily().getId())) {
            throw new QuestionException(QuestionErrorCode.FAMILY_QUESTION_NOT_FOUND);
        }
        return familyQuestion;
    }

    // 원본 에러 메시지에 유니크 제약 여부 확인
    private boolean isDuplicatePoke(DataIntegrityViolationException e){
        String message=e.getMostSpecificCause().getMessage();
        return message!=null && message.contains("uk_poke_sender_receiver_date");
    }
}
