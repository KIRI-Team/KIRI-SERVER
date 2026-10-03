package com.kiri.server.domain.member.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.enums.SocialProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderId(SocialProvider socialProvider, String providerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select m
        from Member m
        where m.socialProvider = :socialProvider
        and m.socialProviderUid = :socialProviderUid
    """)
    Optional<Member> findByProviderAndProviderIdForUpdate(@Param("socialProvider") SocialProvider socialProvider, @Param("socialProviderUid") String socialProviderUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select m
        from Member m
        where m.id = :id
       """)
    Optional<Member> findByIdForUpdate(@Param("id") Long id);
}
