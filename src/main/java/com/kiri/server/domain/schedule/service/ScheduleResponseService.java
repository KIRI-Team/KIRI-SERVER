package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.notification.service.NotificationService;
import com.kiri.server.domain.schedule.converter.ScheduleConverter;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScheduleResponseService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final NotificationService notificationService;

    @Transactional
    public ScheduleResDTO.Responded respond(Long memberId, Long scheduleId, ScheduleReqDTO.Response request) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberException(MemberErrorCode.NOT_FOUND);
        }

        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        if (schedule.getProposer() != null && schedule.getProposer().getId().equals(memberId)) {
            throw new ScheduleException(ScheduleErrorCode.NOT_INVITED);
        }

        ScheduleParticipant participant = scheduleParticipantRepository
                .findBySchedule_IdAndMember_IdForUpdate(scheduleId, memberId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.NOT_INVITED));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!schedule.isResponseOpen(now)) {
            throw new ScheduleException(ScheduleErrorCode.RESPONSE_CLOSED);
        }

        ParticipantStatus previousParticipantStatus = participant.getStatus();
        ParticipantStatus newParticipantStatus = request.action().toParticipantStatus();

        participant.respond(newParticipantStatus, now);

        ScheduleStatus previousStatus = schedule.getStatus();

        long acceptedExcludingProposer = scheduleParticipantRepository
                .countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(
                        scheduleId,
                        ParticipantStatus.ACCEPTED,
                        schedule.getProposer().getId());

        schedule.recalculateStatus(acceptedExcludingProposer, now);

        if (previousParticipantStatus != newParticipantStatus) {
            notificationService.createScheduleResponseNotification(schedule, participant);
        }

        if (previousStatus == ScheduleStatus.PENDING_RESPONSE && schedule.getStatus() == ScheduleStatus.CONFIRMED) {
            notificationService.createScheduleConfirmedNotifications(schedule);
        }

        return ScheduleConverter.toResponded(schedule, participant);
    }

    @Transactional
    public void finalizeExpired(Long scheduleId, LocalDateTime now) {
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElse(null);
        if (schedule == null || schedule.getStatus() != ScheduleStatus.PENDING_RESPONSE
                || schedule.getProposer() == null) {
            return;
        }
        long acceptedExcludingProposer = scheduleParticipantRepository
                .countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(scheduleId,
                        ParticipantStatus.ACCEPTED, schedule.getProposer().getId());
        ScheduleStatus previousStatus = schedule.getStatus();
        schedule.recalculateStatus(acceptedExcludingProposer, now);
        if (previousStatus == ScheduleStatus.PENDING_RESPONSE
                && schedule.getStatus() == ScheduleStatus.NOT_CONFIRMED) {
            notificationService.createScheduleNotConfirmedNotification(schedule);
        }
    }
}
