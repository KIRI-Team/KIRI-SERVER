package com.kiri.server.domain.term.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.term.entity.MemberTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberTermRepository extends JpaRepository<MemberTerm, Long> {

    List<MemberTerm> findAllByMember(Member member);


    @Query("""
            SELECT CASE WHEN COUNT(t) = 0 THEN true ELSE false END
            FROM Term t
            WHERE t.isRequired = true
              AND NOT EXISTS (
                  SELECT mt.id FROM MemberTerm mt
                  WHERE mt.term = t
                    AND mt.member.id = :memberId
                    AND mt.agreedAt IS NOT NULL
                    AND mt.termVersion = t.version
              )
            """)
    boolean areAllRequiredTermsAgreed(@Param("memberId") Long memberId);

    void deleteAllByMemberId(Long memberId);
}
