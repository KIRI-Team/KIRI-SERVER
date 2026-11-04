package com.kiri.server.domain.bucket.service;

import com.kiri.server.domain.bucket.converter.BucketConverter;
import com.kiri.server.domain.bucket.dto.BucketReqDTO;
import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.exception.BucketException;
import com.kiri.server.domain.bucket.exception.code.BucketErrorCode;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.bucket.repository.BucketItemRepository.SaverBucketCount;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BucketQueryService {

    private static final int DEFAULT_SIZE = 20;

    private final MemberRepository memberRepository;
    private final BucketItemRepository bucketItemRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BucketResDTO.FamilyBuckets getFamilyBuckets(Long memberId, BucketReqDTO.FamilyBucketCondition condition) {
        Family family = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND))
                .getFamily();
        if (family == null) {
            throw new BucketException(BucketErrorCode.FAMILY_REQUIRED);
        }

        int size = condition.size() == null ? DEFAULT_SIZE : condition.size();
        List<BucketItem> found = findPage(family.getId(), condition, size + 1);
        boolean hasNext = found.size() > size;
        List<BucketItem> page = hasNext ? found.subList(0, size) : found;

        LocalDate today = LocalDate.now(clock);
        List<BucketResDTO.FamilyBucketItem> items = page.stream()
                .map(item -> BucketConverter.toFamilyBucketItem(item,
                        isSchedulable(item, today), isDeletable(item, memberId)))
                .toList();

        return BucketConverter.toFamilyBuckets(saverCounts(family), items,
                hasNext ? page.getLast().getId() : null, hasNext);
    }

    private List<BucketItem> findPage(Long familyId, BucketReqDTO.FamilyBucketCondition condition, int limit) {
        Long cursor = condition.cursor() == null ? Long.MAX_VALUE : condition.cursor();
        PageRequest pageRequest = PageRequest.of(0, limit);
        if (condition.memberId() == null) {
            return bucketItemRepository.findByFamilyIdAndIdLessThanOrderByIdDesc(familyId, cursor, pageRequest);
        }
        return bucketItemRepository.findByFamilyIdAndMemberIdAndIdLessThanOrderByIdDesc(
                familyId, condition.memberId(), cursor, pageRequest);
    }

    private List<BucketResDTO.SaverCount> saverCounts(Family family) {
        Map<Long, Long> counts = bucketItemRepository.findSaverCounts(family.getId()).stream()
                .collect(Collectors.toMap(SaverBucketCount::getMemberId, SaverBucketCount::getBucketCount));
        return memberRepository.findAllByFamilyAndDeletedAtIsNull(family).stream()
                .sorted(Comparator.comparing(Member::getId))
                .map(member -> BucketConverter.toSaverCount(member, counts.getOrDefault(member.getId(), 0L)))
                .toList();
    }

    private boolean isSchedulable(BucketItem item, LocalDate today) {
        Mission mission = item.getMission();
        return item.getStatus() == BucketStatus.SAVED
                && mission.getStatus() == MissionStatus.ACTIVE
                && (mission.getEndDate() == null || !mission.getEndDate().isBefore(today));
    }

    private boolean isDeletable(BucketItem item, Long memberId) {
        return item.getStatus() == BucketStatus.SAVED
                && item.getMember() != null
                && item.getMember().getId().equals(memberId);
    }
}
