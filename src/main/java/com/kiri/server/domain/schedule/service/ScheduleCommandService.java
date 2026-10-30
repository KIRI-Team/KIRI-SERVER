package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionType;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleCommandService {

    private static final long RESPONSE_DEADLINE_HOURS = 24;

    private final MemberRepository memberRepository;
    private final BucketItemRepository bucketItemRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final Clock clock;

    @Transactional
    public ScheduleResDTO.Created create(Long memberId, Long bucketItemId, ScheduleReqDTO.Create request) {
        Member proposer = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = proposer.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        BucketItem bucketItem = bucketItemRepository.findByIdForUpdate(bucketItemId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.BUCKET_NOT_FOUND));
        if (bucketItem.getFamily() == null || !bucketItem.getFamily().getId().equals(family.getId())) {
            throw new ScheduleException(ScheduleErrorCode.BUCKET_FORBIDDEN);
        }
        if (bucketItem.getStatus() != BucketStatus.SAVED
                || scheduleRepository.existsByBucketItem_IdAndStatusIn(bucketItemId, ScheduleStatus.ACTIVE_STATUSES)) {
            throw new ScheduleException(ScheduleErrorCode.BUCKET_NOT_SCHEDULABLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startAt = validateDateTime(request, bucketItem.getMission(), now);
        List<Long> participantMemberIds = resolveParticipants(request.participantMemberIds(), proposer, family);

        LocalDateTime responseDeadline = calculateDeadline(startAt, now);
        Schedule schedule = scheduleRepository.save(Schedule.create(
                family, bucketItem, proposer,
                request.scheduleDate(), request.scheduleTime(), request.requestMessage(), responseDeadline));

        List<ScheduleParticipant> participants = new ArrayList<>();
        participants.add(ScheduleParticipant.of(schedule, proposer, ParticipantStatus.ACCEPTED, now));
        participantMemberIds.forEach(id ->
                participants.add(ScheduleParticipant.of(schedule, memberRepository.getReferenceById(id),
                        ParticipantStatus.PENDING, null)));
        scheduleParticipantRepository.saveAll(participants);

        bucketItem.markScheduled();

        return ScheduleConverter.toCreated(schedule, participantMemberIds);
    }

    @Transactional
    public ScheduleResDTO.Cancelled cancel(Long memberId, Long scheduleId) {
        if (!memberRepository.existsById(memberId)) {
            throw new MemberException(MemberErrorCode.NOT_FOUND);
        }

        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.isProposer(memberId)) {
            throw new ScheduleException(ScheduleErrorCode.NOT_PROPOSER);
        }
        if (!schedule.isCancelable()) {
            throw new ScheduleException(ScheduleErrorCode.NOT_CANCELABLE);
        }

        schedule.cancel();
        BucketItem bucketItem = schedule.getBucketItem();
        if (bucketItem != null) {
            bucketItem.markSaved();
        }

        return ScheduleConverter.toCancelled(schedule);
    }

    private LocalDateTime validateDateTime(ScheduleReqDTO.Create request, Mission mission, LocalDateTime now) {
        LocalDateTime startAt = LocalDateTime.of(request.scheduleDate(), request.scheduleTime());
        if (!startAt.isAfter(now)) {
            throw new ScheduleException(ScheduleErrorCode.PAST_SCHEDULE_DATETIME);
        }
        if (mission != null && mission.getMissionType() == MissionType.PERIOD) {
            LocalDate date = request.scheduleDate();
            LocalDate start = mission.getStartDate();
            LocalDate end = mission.getEndDate();
            if ((start != null && date.isBefore(start)) || (end != null && date.isAfter(end))) {
                throw new ScheduleException(ScheduleErrorCode.OUT_OF_EVENT_PERIOD);
            }
        }
        return startAt;
    }

    private List<Long> resolveParticipants(List<Long> requested, Member proposer, Family family) {
        Set<Long> familyMemberIds = memberRepository.findAllByFamilyAndDeletedAtIsNull(family).stream()
                .map(Member::getId)
                .collect(Collectors.toSet());
        Set<Long> unique = new LinkedHashSet<>(requested);
        for (Long id : unique) {
            if (id.equals(proposer.getId()) || !familyMemberIds.contains(id)) {
                throw new ScheduleException(ScheduleErrorCode.INVALID_PARTICIPANT);
            }
        }
        return new ArrayList<>(unique);
    }

    private LocalDateTime calculateDeadline(LocalDateTime startAt, LocalDateTime now) {
        LocalDateTime cutoff = startAt.minusHours(RESPONSE_DEADLINE_HOURS);
        return Duration.between(now, startAt).toHours() >= RESPONSE_DEADLINE_HOURS ? cutoff : startAt;
    }
}
