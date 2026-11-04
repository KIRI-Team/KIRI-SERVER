package com.kiri.server.domain.question.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.family.repository.FamilyRepository;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.question.converter.MemberAnswerConverter;
import com.kiri.server.domain.question.converter.QuestionConverter;
import com.kiri.server.domain.question.dto.PreferenceClassification;
import com.kiri.server.domain.question.dto.QuestionReqDTO;
import com.kiri.server.domain.question.dto.QuestionResDTO;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import com.kiri.server.domain.question.entity.Question;
import com.kiri.server.domain.question.enums.TasteType;
import com.kiri.server.domain.question.event.PokeEvent;
import com.kiri.server.domain.question.exception.QuestionException;
import com.kiri.server.domain.question.exception.code.QuestionErrorCode;
import com.kiri.server.domain.question.repository.*;
import com.kiri.server.global.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    // 가족 생성일을 1일차로 보고 7일차까지 고정 질문 배정
    private static final int LAST_FIXED_DAY = 7;

    private final MemberRepository memberRepository;
    private final FamilyQuestionRepository familyQuestionRepository;
    private final MemberAnswerRepository memberAnswerRepository;
    private final MemberAnswerCategoryRepository memberAnswerCategoryRepository;
    private final Clock clock;
    private final PokeRepository pokeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FamilyRepository familyRepository;
    private final QuestionRepository questionRepository;
    private final AiService aiService;

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
    public void poke(Long memberId, Long receiverId) {

        // 자기 자신은 찌를 수 없음
        if (memberId.equals(receiverId)) {
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
        Member receiver = memberRepository.findById(receiverId)
                .orElseThrow(() -> new QuestionException(QuestionErrorCode.POKE_RECEIVER_NOT_FOUND));

        // 대상이 다른 가족
        Family receiverFamily = receiver.getFamily();
        if (receiverFamily == null || !family.getId().equals(receiverFamily.getId())) {
            throw new QuestionException(QuestionErrorCode.POKE_RECEIVER_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);

        // 같은 사람에게 하루 한 번만 가능
        if (pokeRepository.existsBySenderAndReceiverAndPokeDate(member, receiver, today)) {
            throw new QuestionException(QuestionErrorCode.ALREADY_POKED);
        }

        try {
            pokeRepository.save(QuestionConverter.toPoke(member, receiver, today));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicatePoke(e)) {
                throw new QuestionException(QuestionErrorCode.ALREADY_POKED);
            }
            throw e;
        }

        eventPublisher.publishEvent(new PokeEvent(memberId, receiverId));
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

    // 유니크 제약 여부 확인
    private boolean isDuplicatePoke(DataIntegrityViolationException e) {
        if (!(e.getMostSpecificCause() instanceof PSQLException psqlException)) {
            return false;
        }
        ServerErrorMessage serverError = psqlException.getServerErrorMessage();
        return serverError != null && "uk_poke_sender_receiver_date".equals(serverError.getConstraint());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assign(Long familyId, LocalDate date) {

        Optional<Family> foundFamily = familyRepository.findById(familyId);
        if (foundFamily.isEmpty()) {
            log.warn("질문 배정 대상 가족 없음 familyId={}", familyId);
            return;
        }
        Family family = foundFamily.get();

        // 이미 배정된 날짜면 건너뜀
        if (familyQuestionRepository.existsByFamilyAndQuestionDate(family, date)) {
            return;
        }

        // 가족 생성일이 1일차
        int day = (int) ChronoUnit.DAYS.between(family.getCreatedAt().toLocalDate(), date) + 1;
        if (day < 1) {
            return;
        }

        Optional<Question> question = pickFixed(day).or(() -> pickFromPool(family));
        if (question.isEmpty()) {
            log.warn("배정 가능한 질문 없음 familyId={}, date={}", familyId, date);
            return;
        }

        familyQuestionRepository.save(QuestionConverter.toFamilyQuestion(family, question.get(), date));
    }

    @Transactional
    public void saveAnswer(MemberAnswer answer) {
        TasteType tasteType = answer.getFamilyQuestion().getQuestion().getTasteType();

        if (tasteType == null) {
            return;
        }

        String question = answer.getFamilyQuestion().getQuestion().getContent();

        PreferenceClassification classification = aiService.classify(tasteType, question, answer.getContent());

        answer.updateClassification(classification.indoorOutdoor(), classification.durationLevel(), classification.priceLevel(), classification.activityLevel());

        if (tasteType == TasteType.CATEGORY) {
            memberAnswerCategoryRepository.deleteAllByMemberAnswerId(answer.getId());

            List<MemberAnswerCategory> categories =
                    classification.categories().stream()
                            .map(category -> MemberAnswerConverter.toMemberAnswerCategory(answer, category))
                            .toList();

            memberAnswerCategoryRepository.saveAll(categories);
        }
    }

    // 1~7일차 고정 질문
    private Optional<Question> pickFixed(int day) {
        if (day > LAST_FIXED_DAY) {
            return Optional.empty();
        }
        return questionRepository.findByFixedDayAndIsActiveTrue(day);
    }

    // 미사용 질문 중 가족이 적게 받은 취향 종류에서 무작위로, 다 썼으면 가장 오래전 받은 질문 재배정
    private Optional<Question> pickFromPool(Family family) {

        List<Question> unused = questionRepository.findUnassignedPoolQuestions(family);
        if (unused.isEmpty()) {
            return questionRepository.findAssignedPoolQuestionsOrderByLastAssigned(family).stream().findFirst();
        }

        Map<TasteType, Long> assignedCount = countAssignedByTasteType(family);
        long min = unused.stream()
                .mapToLong(q -> assignedCount.getOrDefault(q.getTasteType(), 0L))
                .min()
                .orElseThrow();

        List<Question> candidates = unused.stream()
                .filter(q -> assignedCount.getOrDefault(q.getTasteType(), 0L) == min)
                .toList();

        return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
    }

    // 일반 질문은 null 키로 집계
    private Map<TasteType, Long> countAssignedByTasteType(Family family) {
        Map<TasteType, Long> counts = new HashMap<>();
        familyQuestionRepository.countAssignedByTasteType(family)
                .forEach(c -> counts.put(c.getTasteType(), c.getAssignedCount()));
        return counts;
    }
}
