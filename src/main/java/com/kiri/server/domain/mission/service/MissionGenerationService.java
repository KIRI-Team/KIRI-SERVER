package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.mission.converter.MissionConverter;
import com.kiri.server.domain.mission.entity.Mission;
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

@Service
@RequiredArgsConstructor
public class MissionGenerationService {

    private static final double SAME_PLACE_METERS = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final PlaceRepository placeRepository;
    private final MissionRepository missionRepository;
    private final MissionChecklistItemRepository missionChecklistItemRepository;

    @Transactional
    public void apply(ExternalRecord record, DraftResult result, LocalDate today) {
        switch (result) {
            case DraftResult.Ready ready -> applyDraft(record, ready.draft(), today);
            case DraftResult.ReviewRequired review -> {
                record.markReviewRequired(review.reason());
                requireCheck(record);
            }
            case DraftResult.Ignored ignored -> {
                record.markIgnored();
                requireCheck(record);
            }
        }
    }

    @Transactional
    public void hide(ExternalRecord record) {
        requireCheck(record);
    }

    @Transactional
    public int endExpiredMissions(LocalDate today, LocalDateTime now) {
        return missionRepository.endExpired(today, now);
    }

    private void applyDraft(ExternalRecord record, MissionDraft draft, LocalDate today) {
        Mission mission = record.getMission();
        if (mission == null && draft.isPeriodOver(today)) {
            record.markIgnored();
            return;
        }

        Place place = findOrCreatePlace(draft.place());
        if (mission == null) {
            mission = missionRepository.save(MissionConverter.toMission(draft, place));
        } else {
            mission.updateContent(draft, place);
            mission.reactivate(today);
            missionChecklistItemRepository.deleteAllByMissionInBulk(mission);
        }
        missionChecklistItemRepository.saveAll(MissionConverter.toChecklistItems(mission, draft.checklist()));
        record.markProcessed(mission);
    }

    private Place findOrCreatePlace(MissionDraft.PlaceDraft draft) {
        return placeRepository.findAllByName(draft.name()).stream()
                .filter(place -> distanceMeters(place.getLatitude(), place.getLongitude(),
                        draft.latitude(), draft.longitude()) <= SAME_PLACE_METERS)
                .findFirst()
                .map(place -> {
                    place.fillMissingFrom(draft);
                    return place;
                })
                .orElseGet(() -> placeRepository.save(MissionConverter.toPlace(draft)));
    }

    private void requireCheck(ExternalRecord record) {
        if (record.getMission() != null) {
            record.getMission().requireCheck();
        }
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
}
