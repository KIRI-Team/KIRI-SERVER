package com.kiri.server.domain.setting.repository;

import com.kiri.server.domain.setting.entity.MemberSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberSettingRepository extends JpaRepository<MemberSetting, Long> {
    void deleteAllByMemberId(Long memberId);
}
