package com.kiri.server.domain.taste.caculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class GeneralScoreCalculator {

    private static final BigDecimal DISTANCE_WEIGHT = new BigDecimal("0.7");
    private static final BigDecimal NEW_MISSION_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal MAX_DISTANCE_KM = new BigDecimal("20");
    private static final Duration NEW_MISSION_PERIOD = Duration.ofDays(7);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int SCALE = 10;

    public BigDecimal calculate(BigDecimal homeLatitude, BigDecimal homeLongitude, BigDecimal missionLatitude, BigDecimal missionLongitude, LocalDateTime missionCreatedAt, LocalDateTime now) {
        BigDecimal newMissionScore = calculateNewMissionScore(missionCreatedAt, now);

        if (homeLatitude == null || homeLongitude == null) {
            return newMissionScore;
        }

        BigDecimal distanceScore = calculateDistanceScore(homeLatitude, homeLongitude, missionLatitude, missionLongitude);

        return distanceScore.multiply(DISTANCE_WEIGHT).add(newMissionScore.multiply(NEW_MISSION_WEIGHT)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDistanceScore(BigDecimal homeLatitude, BigDecimal homeLongitude, BigDecimal missionLatitude, BigDecimal missionLongitude) {
        if (missionLatitude == null || missionLongitude == null) {
            return BigDecimal.ZERO;
        }

        double distance = calculateDistance(homeLatitude.doubleValue(), homeLongitude.doubleValue(), missionLatitude.doubleValue(), missionLongitude.doubleValue());

        BigDecimal score = BigDecimal.ONE.subtract(BigDecimal.valueOf(distance).divide(MAX_DISTANCE_KM, SCALE, RoundingMode.HALF_UP));

        return score.max(BigDecimal.ZERO);
    }

    private BigDecimal calculateNewMissionScore(LocalDateTime missionCreatedAt, LocalDateTime now) {
        if (missionCreatedAt == null || now == null) {
            return BigDecimal.ZERO;
        }

        Duration elapsed = Duration.between(missionCreatedAt, now);

        if (elapsed.isNegative() || elapsed.compareTo(NEW_MISSION_PERIOD) > 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.ONE;
    }

    private double calculateDistance(double latitude1, double longitude1, double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);

        double latDiff = Math.toRadians(latitude2 - latitude1);
        double lonDiff = Math.toRadians(longitude2 - longitude1);

        double a = Math.pow(Math.sin(latDiff / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(lonDiff / 2), 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
