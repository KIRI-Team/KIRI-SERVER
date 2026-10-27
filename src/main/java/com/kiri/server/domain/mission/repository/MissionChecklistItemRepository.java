package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MissionChecklistItemRepository extends JpaRepository<MissionChecklistItem, Long> {

    List<MissionChecklistItem> findAllByMissionOrderByItemTypeAscDisplayOrderAsc(Mission mission);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MissionChecklistItem i WHERE i.mission = :mission")
    void deleteAllByMissionInBulk(@Param("mission") Mission mission);
}
