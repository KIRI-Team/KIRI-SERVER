package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
            Long familyId, LocalDate start, LocalDate end);

    boolean existsByBucketItem_IdAndStatusIn(Long bucketItemId, Collection<ScheduleStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT s.id FROM Schedule s WHERE s.status = :status AND s.responseDeadline < :now")
    List<Long> findIdsByStatusAndDeadlineBefore(@Param("status") ScheduleStatus status, @Param("now") LocalDateTime now);
}
