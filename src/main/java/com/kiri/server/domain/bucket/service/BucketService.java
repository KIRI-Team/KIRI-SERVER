package com.kiri.server.domain.bucket.service;

import com.kiri.server.domain.bucket.converter.BucketConverter;
import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.exception.BucketException;
import com.kiri.server.domain.bucket.exception.code.BucketErrorCode;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.domain.mission.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BucketService {

    private static final Set<MissionStatus> VISIBLE_STATUSES = Set.of(MissionStatus.ACTIVE, MissionStatus.ENDED);

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;
    private final BucketItemRepository bucketItemRepository;
    private final Clock clock;

    @Transactional
    public BucketResDTO.Saved saveBucket(Long memberId, Long missionId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        Family family = member.getFamily();
        if (family == null) {
            throw new BucketException(BucketErrorCode.FAMILY_REQUIRED);
        }

        Mission mission = missionRepository.findById(missionId)
                .filter(found -> VISIBLE_STATUSES.contains(found.getStatus()))
                .orElseThrow(() -> new MissionException(MissionErrorCode.NOT_FOUND));
        if (!isSavable(mission)) {
            throw new BucketException(BucketErrorCode.MISSION_NOT_SAVABLE);
        }

        if (bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(memberId, missionId, BucketStatus.ACTIVE_STATUSES)) {
            throw new BucketException(BucketErrorCode.ALREADY_SAVED);
        }

        BucketItem item = bucketItemRepository.save(
                BucketConverter.toBucketItem(family, member, mission, LocalDateTime.now(clock)));

        return BucketConverter.toSaved(item);
    }

    @Transactional
    public void deleteBucket(Long memberId, Long bucketItemId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        BucketItem item = bucketItemRepository.findByIdForUpdate(bucketItemId)
                .filter(found -> isSameFamily(found, member.getFamily()))
                .orElseThrow(() -> new BucketException(BucketErrorCode.NOT_FOUND));

        if (item.getMember() == null || !item.getMember().getId().equals(memberId)) {
            throw new BucketException(BucketErrorCode.NOT_OWNER);
        }
        if (item.getStatus() == BucketStatus.SCHEDULED) {
            throw new BucketException(BucketErrorCode.SCHEDULED_NOT_DELETABLE);
        }
        if (item.getStatus() == BucketStatus.COMPLETED) {
            throw new BucketException(BucketErrorCode.COMPLETED_NOT_DELETABLE);
        }

        bucketItemRepository.delete(item);
    }

    private boolean isSavable(Mission mission) {
        return mission.getStatus() == MissionStatus.ACTIVE
                && (mission.getEndDate() == null || !mission.getEndDate().isBefore(LocalDate.now(clock)));
    }

    private boolean isSameFamily(BucketItem item, Family family) {
        return family != null && item.getFamily().getId().equals(family.getId());
    }
}
