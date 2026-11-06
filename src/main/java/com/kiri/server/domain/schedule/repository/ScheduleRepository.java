package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
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

    boolean existsByFamily_IdAndBucketItem_Mission_IdAndStatusIn(Long familyId, Long missionId,
                                                                Collection<ScheduleStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT s FROM Schedule s
            JOIN FETCH s.bucketItem bi
            JOIN FETCH bi.mission
            WHERE s.id = :scheduleId
              AND s.family.id = :familyId
            """)
    Optional<Schedule> findWithMissionByIdAndFamilyId(@Param("scheduleId") Long scheduleId,
                                                      @Param("familyId") Long familyId);

    @Query("SELECT s.id FROM Schedule s WHERE s.status = :status AND s.responseDeadline < :now")
    List<Long> findIdsByStatusAndDeadlineBefore(@Param("status") ScheduleStatus status, @Param("now") LocalDateTime now);

    @Query("""
            SELECT s FROM Schedule s
            LEFT JOIN FETCH s.bucketItem bi
            LEFT JOIN FETCH bi.mission
            WHERE s.family.id = :familyId
              AND s.status IN :activeStatuses
              AND s.responseDeadline >= :now
              AND EXISTS (SELECT 1 FROM ScheduleParticipant p
                          WHERE p.schedule = s AND p.member.id = :memberId
                            AND (p.status = :pendingStatus OR p.reconfirmRequired = true))
            ORDER BY s.scheduleDate ASC, s.scheduleTime ASC
            """)
    List<Schedule> findResponseRequired(@Param("familyId") Long familyId,
                                        @Param("memberId") Long memberId,
                                        @Param("activeStatuses") Collection<ScheduleStatus> activeStatuses,
                                        @Param("pendingStatus") ParticipantStatus pendingStatus,
                                        @Param("now") LocalDateTime now);

    @Query("""
            SELECT s FROM Schedule s
            LEFT JOIN FETCH s.bucketItem bi
            LEFT JOIN FETCH bi.mission
            WHERE s.family.id = :familyId
              AND s.status = :confirmedStatus
              AND s.scheduleDate BETWEEN :startDate AND :endDate
              AND EXISTS (SELECT 1 FROM ScheduleParticipant p
                          WHERE p.schedule = s AND p.member.id = :memberId
                            AND p.status = :acceptedStatus AND p.reconfirmRequired = false)
            ORDER BY s.scheduleDate ASC, s.scheduleTime ASC
            """)
    List<Schedule> findUpcomingConfirmed(@Param("familyId") Long familyId,
                                         @Param("memberId") Long memberId,
                                         @Param("confirmedStatus") ScheduleStatus confirmedStatus,
                                         @Param("acceptedStatus") ParticipantStatus acceptedStatus,
                                         @Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);
}
