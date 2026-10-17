package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.config.MissionProperties;
import com.kiri.server.domain.mission.converter.MissionConverter;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.mission.repository.MissionChecklistItemRepository;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MissionGenerationService {

    private static final double SAME_PLACE_METERS = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final BigDecimal NEAR_LATITUDE_DELTA = new BigDecimal("0.0010");
    private static final BigDecimal NEAR_LONGITUDE_DELTA = new BigDecimal("0.0013");
    private static final String TITLE_CANCEL_REASON = "원본 제목에 취소 표시";

    private final PlaceRepository placeRepository;
    private final MissionRepository missionRepository;
    private final MissionChecklistItemRepository missionChecklistItemRepository;
    private final MissionProperties missionProperties;

    @Transactional
    public void apply(ExternalRecord record, DraftResult result, LocalDate today) {
        switch (result) {
            case DraftResult.Ready ready -> applyDraft(record, ready.draft(), today);
            case DraftResult.ReviewRequired review -> review(record, review.reason());
            case DraftResult.Ignored ignored -> ignore(record, ignored.reason());
        }
    }

    @Transactional
    public void hide(ExternalRecord record) {
        review(record, ReviewReason.ORIGIN_UNAVAILABLE);
    }

    @Transactional
    public int endExpiredMissions(LocalDate today, LocalDateTime now) {
        return missionRepository.endExpired(today, now);
    }

    @Transactional
    public int publishPendingMissions(LocalDate today, LocalDateTime now) {
        return missionProperties.autoPublish() ? missionRepository.publishPending(today, now) : 0;
    }

    private void applyDraft(ExternalRecord record, MissionDraft draft, LocalDate today) {
        Mission mission = record.getMission();
        if (mission == null && draft.isPeriodOver(today)) {
            record.markIgnored(ReviewReason.PERIOD_OVER);
            return;
        }

        PlaceMatch match = matchPlace(draft.place());
        if (match.ambiguous()) {
            review(record, ReviewReason.PLACE_AMBIGUOUS);
            return;
        }
        if (mission == null) {
            Optional<ReviewReason> duplicate = findDuplicate(draft);
            if (duplicate.isPresent()) {
                if (duplicate.get() == ReviewReason.DUPLICATE) {
                    record.markIgnored(ReviewReason.DUPLICATE);
                } else {
                    record.markReviewRequired(duplicate.get());
                }
                return;
            }
        }

        Place place = match.place()
                .map(existing -> refresh(existing, draft.place()))
                .orElseGet(() -> placeRepository.save(MissionConverter.toPlace(draft.place())));

        if (mission == null) {
            MissionStatus status = missionProperties.autoPublish() ? MissionStatus.ACTIVE : MissionStatus.CHECK_REQUIRED;
            mission = missionRepository.save(MissionConverter.toMission(draft, place, status));
            saveChecklist(mission, draft);
        } else {
            mission.updateContent(draft, place);
            if (missionProperties.autoPublish()) {
                mission.reactivate(today);
            } else {
                mission.endIfPeriodOver(today);
            }
            if (!mission.isChecklistLocked()) {
                missionChecklistItemRepository.deleteAllByMissionInBulk(mission);
                saveChecklist(mission, draft);
            }
        }
        record.markProcessed(mission);
    }

    private void saveChecklist(Mission mission, MissionDraft draft) {
        missionChecklistItemRepository.saveAll(MissionConverter.toChecklistItems(mission, draft.checklist()));
    }

    private void review(ExternalRecord record, ReviewReason reason) {
        record.markReviewRequired(reason);
        if (record.getMission() != null) {
            record.getMission().requireCheck();
        }
    }

    private void ignore(ExternalRecord record, ReviewReason reason) {
        record.markIgnored(reason);
        Mission mission = record.getMission();
        if (mission == null) {
            return;
        }
        if (reason == ReviewReason.EXTERNAL_CANCELLED) {
            mission.cancelByExternal(TITLE_CANCEL_REASON);
        } else {
            mission.requireCheck();
        }
    }

    private Place refresh(Place place, MissionDraft.PlaceDraft draft) {
        place.fillMissingFrom(draft);
        place.refreshImage(draft.imageUrl(), draft.imageSource());
        return place;
    }

    private PlaceMatch matchPlace(MissionDraft.PlaceDraft draft) {
        List<Place> nearby = placeRepository.findAllWithin(
                        draft.latitude().subtract(NEAR_LATITUDE_DELTA), draft.latitude().add(NEAR_LATITUDE_DELTA),
                        draft.longitude().subtract(NEAR_LONGITUDE_DELTA), draft.longitude().add(NEAR_LONGITUDE_DELTA))
                .stream()
                .filter(place -> isNear(place, draft))
                .toList();

        Optional<Place> same = nearby.stream().filter(place -> isSamePlace(place, draft)).findFirst();
        if (same.isPresent()) {
            return new PlaceMatch(same, false);
        }
        boolean sameNameNearby = nearby.stream().anyMatch(place -> sameName(place.getName(), draft.name()));
        boolean sameNameAndAddressFar = placeRepository.findAllByName(draft.name()).stream()
                .anyMatch(place -> sameAddress(place.getAddress(), draft.address()) && !isNear(place, draft));
        return new PlaceMatch(Optional.empty(), sameNameNearby || sameNameAndAddressFar);
    }

    private Optional<ReviewReason> findDuplicate(MissionDraft draft) {
        List<Mission> candidates = missionRepository.findAllWithPlaceByTitle(draft.title());
        if (candidates.stream().anyMatch(existing ->
                isSamePlace(existing.getPlace(), draft.place()) && samePeriod(existing, draft))) {
            return Optional.of(ReviewReason.DUPLICATE);
        }
        if (candidates.stream().anyMatch(existing ->
                isNear(existing.getPlace(), draft.place()) && overlaps(existing, draft))) {
            return Optional.of(ReviewReason.DUPLICATE_SUSPECT);
        }
        return Optional.empty();
    }

    private boolean isSamePlace(Place place, MissionDraft.PlaceDraft draft) {
        return sameName(place.getName(), draft.name())
                && addressMatches(place.getAddress(), draft.address())
                && isNear(place, draft);
    }

    private boolean isNear(Place place, MissionDraft.PlaceDraft draft) {
        return distanceMeters(place.getLatitude(), place.getLongitude(),
                draft.latitude(), draft.longitude()) <= SAME_PLACE_METERS;
    }

    private boolean samePeriod(Mission existing, MissionDraft draft) {
        if (existing.getMissionType() != draft.missionType()) {
            return false;
        }
        return draft.missionType() == MissionType.PERMANENT
                || (Objects.equals(existing.getStartDate(), draft.startDate())
                && Objects.equals(existing.getEndDate(), draft.endDate()));
    }

    private boolean overlaps(Mission existing, MissionDraft draft) {
        if (existing.getMissionType() != draft.missionType()) {
            return false;
        }
        if (draft.missionType() == MissionType.PERMANENT) {
            return true;
        }
        return existing.getStartDate() != null && existing.getEndDate() != null
                && !existing.getStartDate().isAfter(draft.endDate())
                && !draft.startDate().isAfter(existing.getEndDate());
    }

    static boolean sameName(String a, String b) {
        return a != null && b != null && normalizeName(a).equals(normalizeName(b));
    }

    static boolean addressMatches(String a, String b) {
        return a == null || b == null || a.isBlank() || b.isBlank() || sameAddress(a, b);
    }

    static boolean sameAddress(String a, String b) {
        return a != null && b != null && normalizeAddress(a).equals(normalizeAddress(b));
    }

    private static String normalizeName(String name) {
        return name.replaceAll("\\s+", "").toLowerCase();
    }

    private static String normalizeAddress(String address) {
        return address.replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", "");
    }

    static double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private record PlaceMatch(Optional<Place> place, boolean ambiguous) {
    }
}
