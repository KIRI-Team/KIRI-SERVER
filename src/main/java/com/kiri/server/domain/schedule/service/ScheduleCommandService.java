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
import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleConditionRepository;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
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
    private final ScheduleConditionRepository scheduleConditionRepository;
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
        LocalDateTime startAt = validateDateTime(request.scheduleDate(), request.scheduleTime(),
                bucketItem.getMission(), now);
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

    @Transactional
    public void updateConditions(Long memberId, Long scheduleId, ScheduleReqDTO.UpdateConditions request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .filter(found -> found.getFamily().getId().equals(family.getId()))
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.isUpdatable()) {
            throw new ScheduleException(ScheduleErrorCode.CONDITION_NOT_EDITABLE);
        }

        Set<ConditionType> requested = request.conditions().isEmpty()
                ? EnumSet.noneOf(ConditionType.class)
                : EnumSet.copyOf(request.conditions());
        List<ScheduleCondition> existing = scheduleConditionRepository.findBySchedule_Id(scheduleId);
        Set<ConditionType> existingTypes = existing.stream()
                .map(ScheduleCondition::getConditionType)
                .collect(Collectors.toSet());

        List<ScheduleCondition> toDelete = existing.stream()
                .filter(condition -> !requested.contains(condition.getConditionType()))
                .toList();
        List<ScheduleCondition> toSave = requested.stream()
                .filter(type -> !existingTypes.contains(type))
                .map(type -> ScheduleConverter.toScheduleCondition(schedule, type))
                .toList();

        scheduleConditionRepository.deleteAll(toDelete);
        scheduleConditionRepository.saveAll(toSave);
    }

    @Transactional
    public ScheduleResDTO.Updated update(Long memberId, Long scheduleId, ScheduleReqDTO.Update request) {
        Member proposer = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = proposer.getFamily();
        if (family == null) {
            throw new ScheduleException(ScheduleErrorCode.FAMILY_REQUIRED);
        }

        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        if (!schedule.isProposer(memberId)) {
            throw new ScheduleException(ScheduleErrorCode.NOT_PROPOSER);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!schedule.isUpdatable() || schedule.hasStarted(now)) {
            throw new ScheduleException(ScheduleErrorCode.NOT_UPDATABLE);
        }

        Mission mission = schedule.getBucketItem() == null ? null : schedule.getBucketItem().getMission();
        LocalDateTime startAt = validateDateTime(request.scheduleDate(), request.scheduleTime(), mission, now);

        List<Long> targetIds = resolveParticipants(request.participantMemberIds(), proposer, family);
        Set<Long> targetSet = new LinkedHashSet<>(targetIds);

        List<ScheduleParticipant> existing = scheduleParticipantRepository.findBySchedule_Id(scheduleId);
        Set<Long> existingMemberIds = new HashSet<>();
        List<ScheduleParticipant> toRemove = new ArrayList<>();
        List<ScheduleParticipant> keptAccepted = new ArrayList<>();
        for (ScheduleParticipant p : existing) {
            if (p.getMember() == null || p.getMember().getId().equals(proposer.getId())) {
                continue;
            }
            Long id = p.getMember().getId();
            existingMemberIds.add(id);
            if (!targetSet.contains(id)) {
                toRemove.add(p);
            } else if (p.getStatus() == ParticipantStatus.ACCEPTED) {
                keptAccepted.add(p);
            }
        }

        boolean coreChanged = !schedule.getScheduleDate().equals(request.scheduleDate())
                || !schedule.getScheduleTime().equals(request.scheduleTime())
                || !targetSet.equals(existingMemberIds);

        if (!toRemove.isEmpty()) {
            scheduleParticipantRepository.deleteAll(toRemove);
        }
        List<ScheduleParticipant> toAdd = new ArrayList<>();
        targetIds.forEach(id -> {
            if (!existingMemberIds.contains(id)) {
                toAdd.add(ScheduleParticipant.of(schedule, memberRepository.getReferenceById(id),
                        ParticipantStatus.PENDING, null));
            }
        });
        if (!toAdd.isEmpty()) {
            scheduleParticipantRepository.saveAll(toAdd);
        }
        if (coreChanged) {
            keptAccepted.forEach(ScheduleParticipant::markReconfirmRequired);
        }

        LocalDateTime responseDeadline = calculateDeadline(startAt, now);
        schedule.update(request.scheduleDate(), request.scheduleTime(), request.requestMessage(), responseDeadline);

        long acceptedExcludingProposer = scheduleParticipantRepository
                .countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(scheduleId,
                        ParticipantStatus.ACCEPTED, proposer.getId());
        schedule.recalculateStatus(acceptedExcludingProposer, now);

        return ScheduleConverter.toUpdated(schedule, targetIds);
    }

    private LocalDateTime validateDateTime(LocalDate scheduleDate, LocalTime scheduleTime, Mission mission,
                                           LocalDateTime now) {
        LocalDateTime startAt = LocalDateTime.of(scheduleDate, scheduleTime);
        if (!startAt.isAfter(now)) {
            throw new ScheduleException(ScheduleErrorCode.PAST_SCHEDULE_DATETIME);
        }
        if (mission != null && mission.getMissionType() == MissionType.PERIOD) {
            LocalDate start = mission.getStartDate();
            LocalDate end = mission.getEndDate();
            if ((start != null && scheduleDate.isBefore(start)) || (end != null && scheduleDate.isAfter(end))) {
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
