package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipant, Long> {

    List<ScheduleParticipant> findBySchedule_IdInAndMember_Id(List<Long> scheduleIds, Long memberId);

    List<ScheduleParticipant> findBySchedule_Id(Long scheduleId);

    @Query("SELECT p FROM ScheduleParticipant p LEFT JOIN FETCH p.member WHERE p.schedule.id = :scheduleId ORDER BY p.id")
    List<ScheduleParticipant> findAllWithMemberByScheduleId(@Param("scheduleId") Long scheduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ScheduleParticipant p WHERE p.schedule.id = :scheduleId AND p.member.id = :memberId")
    Optional<ScheduleParticipant> findBySchedule_IdAndMember_IdForUpdate(@Param("scheduleId") Long scheduleId,
                                                                         @Param("memberId") Long memberId);

    boolean existsBySchedule_IdAndMember_IdAndStatusAndReconfirmRequiredFalse(Long scheduleId, Long memberId,
                                                                              ParticipantStatus status);

    long countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(Long scheduleId, ParticipantStatus status,
                                                                             Long proposerId);
}
