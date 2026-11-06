package com.kiri.server.domain.family.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.List;
import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    @Query("select f.id from Family f")
    List<Long> findAllIds();

    boolean existsByName(String name);

    boolean existsByInviteCode(String code);

    @Query("""
        SELECT m.family
        FROM Member m
        WHERE m.id = :memberId
        AND m.family IS NOT NULL
        """)
    Optional<Family> findByMemberId(@Param("memberId") Long memberId);
}
