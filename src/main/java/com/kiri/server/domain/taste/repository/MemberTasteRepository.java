package com.kiri.server.domain.taste.repository;

import com.kiri.server.domain.taste.entity.MemberTaste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberTasteRepository extends JpaRepository<MemberTaste, Long> {

    @Query("""
        select t
        from MemberTaste t
        left join fetch t.categories
        where t.member.id = :memberId
    """)
    Optional<MemberTaste> findWithCategoriesByMemberId(@Param("memberId") Long memberId);

    @Query("""
        select distinct t
        from MemberTaste t
        left join fetch t.categories
        where t.member.id in :memberIds
    """)
    List<MemberTaste> findAllWithCategoriesByMemberIdIn(@Param("memberIds") Collection<Long> memberIds);

    @Query("""
        select m.id
        from Member m
        where m.family.id = :familyId
        order by m.id
    """)
    List<Long> findMemberIdsByFamilyId(@Param("familyId") Long familyId);

    void deleteAllByMemberId(Long memberId);
}
