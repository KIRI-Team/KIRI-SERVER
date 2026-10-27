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
import java.util.Optional;

@Repository
public interface MissionRepository extends JpaRepository<Mission, Long> {

    @Query("SELECT m FROM Mission m JOIN FETCH m.place WHERE m.title = :title")
    List<Mission> findAllWithPlaceByTitle(@Param("title") String title);

    @Query("SELECT m FROM Mission m JOIN FETCH m.place WHERE m.id = :id")
    Optional<Mission> findWithPlaceById(@Param("id") Long id);

    @Query("SELECT m FROM Mission m JOIN FETCH m.place WHERE m.id IN :ids")
    List<Mission> findAllWithPlaceByIdIn(@Param("ids") List<Long> ids);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Mission m
               SET m.status = com.kiri.server.domain.mission.enums.MissionStatus.ACTIVE,
                   m.updatedAt = :now
             WHERE m.status = com.kiri.server.domain.mission.enums.MissionStatus.CHECK_REQUIRED
               AND (m.missionType = com.kiri.server.domain.mission.enums.MissionType.PERMANENT OR m.endDate >= :today)
               AND EXISTS (SELECT r.id FROM ExternalRecord r
                            WHERE r.mission = m
                              AND r.processStatus = com.kiri.server.domain.external.enums.ProcessStatus.PROCESSED)
            """)
    int publishPending(@Param("today") LocalDate today, @Param("now") LocalDateTime now);
}
