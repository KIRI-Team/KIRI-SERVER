package com.kiri.server.domain.member.repository;

import com.kiri.server.domain.member.entity.MemberDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberDeviceRepository extends JpaRepository<MemberDevice, Long> {
    void deleteAllByMemberId(Long memberId);
}
