package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.entity.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    @Query("SELECT m FROM Mission m JOIN FETCH m.place WHERE m.title = :title")
    List<Mission> findAllWithPlaceByTitle(@Param("title") String title);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Mission m
               SET m.status = com.kiri.server.domain.mission.enums.MissionStatus.ENDED,
                   m.updatedAt = :now
             WHERE m.status = com.kiri.server.domain.mission.enums.MissionStatus.ACTIVE
               AND m.missionType = com.kiri.server.domain.mission.enums.MissionType.PERIOD
               AND m.endDate < :today
            """)
    int endExpired(@Param("today") LocalDate today, @Param("now") LocalDateTime now);
}
