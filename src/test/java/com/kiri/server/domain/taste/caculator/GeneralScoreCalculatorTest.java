package com.kiri.server.domain.taste.caculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralScoreCalculatorTest {

    private final GeneralScoreCalculator calculator = new GeneralScoreCalculator();

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    private static final BigDecimal HOME_LATITUDE = new BigDecimal("37.5665");
    private static final BigDecimal HOME_LONGITUDE = new BigDecimal("126.9780");

    @Test
    @DisplayName("집과 미션 장소가 같고 신규 미션이면 일반 점수는 1점이다")
    void calculateSameLocationAndNewMission() {
        LocalDateTime createdAt = NOW.minusDays(3);

        BigDecimal result = calculator.calculate(HOME_LATITUDE, HOME_LONGITUDE, HOME_LATITUDE, HOME_LONGITUDE, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("1.0000000000");
    }

    @Test
    @DisplayName("집에서 10km 떨어진 신규 미션의 일반 점수를 계산한다")
    void calculateTenKmAndNewMission() {
        BigDecimal missionLatitude = getLatitudeAfterDistance(10);

        LocalDateTime createdAt = NOW.minusDays(3);

        BigDecimal result = calculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO, missionLatitude, BigDecimal.ZERO, createdAt, NOW);

        assertThat(result).isCloseTo(new BigDecimal("0.65"), org.assertj.core.data.Offset.offset(new BigDecimal("0.000001")));
    }

    @Test
    @DisplayName("집에서 20km 떨어진 신규 미션의 일반 점수는 0.3점이다")
    void calculateTwentyKmAndNewMission() {
        BigDecimal missionLatitude = getLatitudeAfterDistance(20);

        LocalDateTime createdAt = NOW.minusDays(3);

        BigDecimal result = calculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO, missionLatitude, BigDecimal.ZERO, createdAt, NOW);

        assertThat(result).isCloseTo(new BigDecimal("0.3"), org.assertj.core.data.Offset.offset(new BigDecimal("0.000001")));
    }

    @Test
    @DisplayName("집에서 20km 이상 떨어진 미션은 거리 점수가 0점이다")
    void calculateOverTwentyKmAndNewMission() {
        BigDecimal missionLatitude = getLatitudeAfterDistance(25);

        LocalDateTime createdAt = NOW.minusDays(3);

        BigDecimal result = calculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO, missionLatitude, BigDecimal.ZERO, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("0.3");
    }

    @Test
    @DisplayName("미션 등록 후 정확히 7일이 지났으면 신규 점수를 부여한다")
    void calculateExactlySevenDaysOldMission() {
        LocalDateTime createdAt = NOW.minusDays(7);

        BigDecimal result = calculator.calculate(HOME_LATITUDE, HOME_LONGITUDE, HOME_LATITUDE, HOME_LONGITUDE, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("미션 등록 후 7일이 초과하면 신규 점수를 부여하지 않는다")
    void calculateOverSevenDaysOldMission() {
        LocalDateTime createdAt = NOW.minusDays(8);

        BigDecimal result = calculator.calculate(HOME_LATITUDE, HOME_LONGITUDE, HOME_LATITUDE, HOME_LONGITUDE, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("0.7");
    }

    @Test
    @DisplayName("집 위치가 없으면 신규 미션 점수만 사용한다")
    void calculateWithoutHomeLocation() {
        LocalDateTime createdAt = NOW.minusDays(3);

        BigDecimal result = calculator.calculate(null, null, HOME_LATITUDE, HOME_LONGITUDE, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("집 위치가 없고 기존 미션이면 0점을 반환한다")
    void calculateWithoutHomeLocationAndOldMission() {
        LocalDateTime createdAt = NOW.minusDays(8);

        BigDecimal result = calculator.calculate(null, null, HOME_LATITUDE, HOME_LONGITUDE, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("미션 장소 좌표가 없으면 거리 점수 없이 신규 점수만 반영한다")
    void calculateWithoutMissionLocation() {
        LocalDateTime createdAt = NOW.minusDays(3);

        BigDecimal result = calculator.calculate(HOME_LATITUDE, HOME_LONGITUDE, null, null, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("0.3");
    }

    @Test
    @DisplayName("미션 생성 시각이 현재보다 미래이면 신규 점수를 부여하지 않는다")
    void calculateWithFutureMissionCreatedAt() {
        LocalDateTime createdAt = NOW.plusDays(1);

        BigDecimal result = calculator.calculate(HOME_LATITUDE, HOME_LONGITUDE, HOME_LATITUDE, HOME_LONGITUDE, createdAt, NOW);

        assertThat(result).isEqualByComparingTo("0.7");
    }

    private BigDecimal getLatitudeAfterDistance(double distanceKm) {
        double latitudeDifference = Math.toDegrees(distanceKm / 6371.0);

        return BigDecimal.valueOf(latitudeDifference);
    }
}