package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.question.entity.MemberAnswerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberAnswerCategoryRepository extends JpaRepository<MemberAnswerCategory, Long> {
    void deleteAllByMemberAnswerId(Long memberAnswerId);
}
