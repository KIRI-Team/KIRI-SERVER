package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
            Long familyId, LocalDate start, LocalDate end);
}
