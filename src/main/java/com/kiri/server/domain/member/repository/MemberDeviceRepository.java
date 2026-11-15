package com.kiri.server.domain.member.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.entity.MemberDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {

    Optional<MemberDevice> findByMemberAndDeviceIdentifier(Member member, String deviceIdentifier);
    void deleteAllByMemberId(Long memberId);
    List<MemberDevice> findAllByMemberId(Long memberId);
    Optional<MemberDevice> findByFcmToken(String fcmToken);
    @Transactional
    @Modifying
    @Query("DELETE FROM MemberDevice md WHERE md.fcmToken = :fcmToken")
    void deleteByFcmToken(@Param("fcmToken") String fcmToken);
}
