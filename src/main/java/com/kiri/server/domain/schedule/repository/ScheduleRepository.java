package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
            Long familyId, LocalDate start, LocalDate end);

    boolean existsByBucketItem_IdAndStatusIn(Long bucketItemId, Collection<ScheduleStatus> statuses);
}
