package com.kiri.server.domain.mission.service;

import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class NearbyMissionFinder {

    public static final double RADIUS_KM = 5.0;
    public static final double NEAR_DISTANCE_KM = 1.0;

    private static final int LIMIT = 5;
    private static final double KM_PER_DEGREE = 111.0;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final MissionRepository missionRepository;

    public record Candidate(Mission mission, double distanceKm) {
    }

    // 반경 내에서 date에 운영 중인 활성 미션 (기준 장소 제외)
    public List<Mission> findCandidates(Place base, LocalDate date) {
        double latitude = base.getLatitude().doubleValue();
        double longitude = base.getLongitude().doubleValue();
        double latitudeDelta = RADIUS_KM / KM_PER_DEGREE;
        double longitudeDelta = RADIUS_KM / (KM_PER_DEGREE * Math.cos(Math.toRadians(latitude)));

        List<Long> ids = missionRepository.findNearbyIds(latitude, longitude,
                latitude - latitudeDelta, latitude + latitudeDelta,
                longitude - longitudeDelta, longitude + longitudeDelta,
                RADIUS_KM, base.getId(), date);
        return ids.isEmpty() ? List.of() : missionRepository.findAllWithPlaceByIdIn(ids);
    }

    // 점수 높은 순 → 같으면 가까운 순으로 정렬, 장소별 첫 미션 상위 5개
    public List<Candidate> pickTop(List<Mission> candidates, Place base, ToDoubleFunction<Candidate> score) {
        Map<Long, Double> distances = candidates.stream()
                .map(Mission::getPlace)
                .distinct()
                .collect(Collectors.toMap(Place::getId, place -> distanceKm(base, place)));

        Comparator<Candidate> order = Comparator.comparingDouble(score).reversed()
                .thenComparingDouble(Candidate::distanceKm)
                .thenComparing(candidate -> candidate.mission().getId());

        Map<Long, Candidate> representatives = new LinkedHashMap<>();
        candidates.stream()
                .map(mission -> new Candidate(mission, distances.get(mission.getPlace().getId())))
                .sorted(order)
                .forEach(candidate -> representatives.putIfAbsent(candidate.mission().getPlace().getId(), candidate));

        return representatives.values().stream()
                .limit(LIMIT)
                .toList();
    }

    private double distanceKm(Place base, Place place) {
        double phi1 = Math.toRadians(base.getLatitude().doubleValue());
        double phi2 = Math.toRadians(place.getLatitude().doubleValue());
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(place.getLongitude().doubleValue() - base.getLongitude().doubleValue());
        double a = Math.pow(Math.sin(deltaPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(deltaLambda / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
