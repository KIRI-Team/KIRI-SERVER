package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MemberAnswerCategoryRepository extends JpaRepository<MemberAnswerCategory, Long> {
    void deleteAllByMemberAnswerId(Long memberAnswerId);
    List<MemberAnswerCategory> findAllByMemberAnswerIdIn(Collection<Long> memberAnswerIds);
}
