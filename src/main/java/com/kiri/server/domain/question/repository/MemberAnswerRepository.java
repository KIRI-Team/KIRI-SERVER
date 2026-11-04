package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.entity.MemberAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberAnswerRepository extends JpaRepository<MemberAnswer, Long> {
    Optional<MemberAnswer> findByFamilyQuestionAndMember(FamilyQuestion familyQuestion, Member member);

    List<MemberAnswer> findAllByFamilyQuestion(FamilyQuestion familyQuestion);

    List<MemberAnswer> findAllByMemberId(Long memberId);
    List<MemberAnswer> findAllByMember_IdIn(Collection<Long> memberIds);
}
