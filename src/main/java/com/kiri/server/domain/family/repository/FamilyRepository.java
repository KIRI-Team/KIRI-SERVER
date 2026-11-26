package com.kiri.server.domain.family.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.List;
import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    @Query("select f.id from Family f where f.deletedAt is null")
    List<Long> findActiveIds();

    boolean existsByName(String name);

    boolean existsByInviteCode(String code);

    @Query("""
        SELECT m.family
        FROM Member m
        WHERE m.id = :memberId
        AND m.family IS NOT NULL
        """)
    Optional<Family> findByMemberId(@Param("memberId") Long memberId);

    Optional<Family> findByInviteCode(String inviteCode);

    Optional<Family> findByInviteToken(String inviteToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Family f WHERE f.id = :id")
    Optional<Family> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Family f WHERE f.inviteCode = :inviteCode")
    Optional<Family> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Family f WHERE f.inviteToken = :inviteToken")
    Optional<Family> findByInviteTokenForUpdate(@Param("inviteToken") String inviteToken);
}
