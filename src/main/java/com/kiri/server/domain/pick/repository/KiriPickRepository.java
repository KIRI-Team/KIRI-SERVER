package com.kiri.server.domain.pick.repository;

import com.kiri.server.domain.pick.entity.KiriPick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface KiriPickRepository extends JpaRepository<KiriPick, Long> {

    // 지금 노출 중인 편성. 미션이 공개 상태가 아니면 빠진다.
    @Query("""
            SELECT k FROM KiriPick k JOIN FETCH k.mission m
             WHERE k.active = TRUE AND k.startAt <= :now AND k.endAt > :now
               AND m.status = com.kiri.server.domain.mission.enums.MissionStatus.ACTIVE
             ORDER BY k.displayOrder ASC, k.id ASC
            """)
    List<KiriPick> findExposed(@Param("now") LocalDateTime now);

    @Query("SELECT k FROM KiriPick k JOIN FETCH k.mission m JOIN FETCH m.place ORDER BY k.displayOrder ASC, k.id ASC")
    List<KiriPick> findAllWithMission();
}
