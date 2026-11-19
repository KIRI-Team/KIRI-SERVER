package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleConditionRepository extends JpaRepository<ScheduleCondition, Long> {

    List<ScheduleCondition> findBySchedule_Id(Long scheduleId);
}
