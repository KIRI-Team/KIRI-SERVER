package com.kiri.server.domain.external.client.kma;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class KmaGridTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "서울시청, 37.5665, 126.9780, 60, 127",
            "부산시청, 35.1796, 129.0756, 98, 76",
            "제주시청, 33.4996, 126.5312, 53, 38"
    })
    @DisplayName("위경도를 기상청 격자표와 같은 격자 번호로 변환한다.")
    void from(String place, double latitude, double longitude, int nx, int ny) {
        assertThat(KmaGrid.from(latitude, longitude)).isEqualTo(new KmaGrid(nx, ny));
    }
}
