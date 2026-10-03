package com.kiri.server.domain.term.repository;

import com.kiri.server.domain.term.entity.MemberTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberTermRepository extends JpaRepository<MemberTerm, Long> {

    @Query("""
    SELECT CASE WHEN COUNT(t) = (
        SELECT COUNT(mt)
        FROM MemberTerm mt
        WHERE mt.member.id = :memberId
        AND mt.agreedAt IS NOT NULL
        AND mt.term.isRequired = true
    )
    THEN true ELSE false END
    FROM Term t
    WHERE t.isRequired = true
    """)
    boolean areAllRequiredTermsAgreed(@Param("memberId") Long memberId);
}
