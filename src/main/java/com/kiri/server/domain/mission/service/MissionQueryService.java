package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.repository.ExternalRecordRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.exception.code.MemberErrorCode;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.converter.MissionConverter;
import com.kiri.server.domain.mission.dto.MissionReqDTO;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.domain.mission.repository.MissionChecklistItemRepository;
import com.kiri.server.domain.mission.repository.MissionFeedRepository;
import com.kiri.server.domain.mission.repository.MissionFeedRepository.BaseLocation;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MissionQueryService {

    private static final int DEFAULT_SIZE = 20;
    private static final Set<Integer> DISTANCES = Set.of(1, 3, 5, 10);
    private static final Pattern REGION_CODE = Pattern.compile("\\d{2}|\\d{5}");
    private static final Set<MissionStatus> DETAIL_STATUSES = Set.of(MissionStatus.ACTIVE, MissionStatus.ENDED);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final MissionRepository missionRepository;
    private final MissionFeedRepository missionFeedRepository;
    private final MissionChecklistItemRepository missionChecklistItemRepository;
    private final ExternalRecordRepository externalRecordRepository;
    private final MemberRepository memberRepository;
    private final BucketItemRepository bucketItemRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MissionResDTO.Feed getFeed(Long memberId, MissionReqDTO.FeedCondition condition) {
        validate(condition);
        BaseLocation base = resolveBaseLocation(memberId, condition);
        int size = condition.size() == null ? DEFAULT_SIZE : condition.size();

        List<Long> ids = missionFeedRepository.findFeedIds(condition, base, LocalDate.now(clock), size + 1);
        boolean hasNext = ids.size() > size;
        List<Long> pageIds = hasNext ? ids.subList(0, size) : ids;
        if (pageIds.isEmpty()) {
            return MissionConverter.toFeed(List.of(), null, false);
        }

        Map<Long, Mission> missions = missionRepository.findAllWithPlaceByIdIn(pageIds).stream()
                .collect(Collectors.toMap(Mission::getId, Function.identity()));
        List<MissionResDTO.FeedItem> items = pageIds.stream()
                .map(missions::get)
                .filter(Objects::nonNull)
                .map(mission -> MissionConverter.toFeedItem(mission, distanceFrom(base, mission.getPlace())))
                .toList();

        return MissionConverter.toFeed(items, hasNext ? pageIds.getLast() : null, hasNext);
    }

    @Transactional(readOnly = true)
    public MissionResDTO.Detail getDetail(Long memberId, Long missionId) {
        Mission mission = missionRepository.findWithPlaceById(missionId)
                .filter(found -> DETAIL_STATUSES.contains(found.getStatus()))
                .orElseThrow(() -> new MissionException(MissionErrorCode.NOT_FOUND));
        String sourceUrl = externalRecordRepository.findFirstByMissionAndSourceUrlIsNotNullOrderByIdAsc(mission)
                .map(ExternalRecord::getSourceUrl)
                .orElse(null);

        boolean bucketSaved = bucketItemRepository.existsByMemberIdAndMissionIdAndStatusIn(
                memberId, missionId, BucketStatus.ACTIVE_STATUSES);

        return MissionConverter.toDetail(mission,
                missionChecklistItemRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission),
                sourceUrl, bucketSaved);
    }

    private void validate(MissionReqDTO.FeedCondition condition) {
        if ((condition.latitude() == null) != (condition.longitude() == null)) {
            throw new MissionException(MissionErrorCode.COORDINATE_PAIR_REQUIRED);
        }
        if (condition.distance() != null && !DISTANCES.contains(condition.distance())) {
            throw new MissionException(MissionErrorCode.INVALID_DISTANCE);
        }
        if (condition.regionCode() != null && !REGION_CODE.matcher(condition.regionCode()).matches()) {
            throw new MissionException(MissionErrorCode.INVALID_REGION_CODE);
        }
    }

    private BaseLocation resolveBaseLocation(Long memberId, MissionReqDTO.FeedCondition condition) {
        if (condition.hasCoordinates()) {
            return new BaseLocation(condition.latitude(), condition.longitude());
        }
        Family family = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND))
                .getFamily();
        if (family == null || family.getLatitude() == null || family.getLongitude() == null) {
            return null;
        }
        return new BaseLocation(family.getLatitude().doubleValue(), family.getLongitude().doubleValue());
    }

    private Double distanceFrom(BaseLocation base, Place place) {
        if (base == null) {
            return null;
        }
        double phi1 = Math.toRadians(base.latitude());
        double phi2 = Math.toRadians(place.getLatitude().doubleValue());
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(place.getLongitude().doubleValue() - base.longitude());
        double a = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));

        return Math.round(km * 10) / 10.0;
    }
}
