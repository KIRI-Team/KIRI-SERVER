package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipant, Long> {

    List<ScheduleParticipant> findBySchedule_IdInAndMember_Id(List<Long> scheduleIds, Long memberId);
}
