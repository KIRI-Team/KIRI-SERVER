package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MissionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Test
    @DisplayName("새 미션의 기본 상태는 ACTIVE다.")
    void defaultStatus() {
        Mission mission = periodMission(TODAY.minusDays(1), TODAY.plusDays(1));

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
    }

    @Test
    @DisplayName("원본에 접근할 수 없으면 노출 중인 미션을 CHECK_REQUIRED로 숨긴다.")
    void requireCheck_active() {
        Mission mission = periodMission(TODAY, TODAY);

        mission.requireCheck();

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.CHECK_REQUIRED);
    }

    @Test
    @DisplayName("운영자가 숨긴 미션은 원본 접근 불가로 상태를 바꾸지 않는다.")
    void requireCheck_hiddenUnchanged() {
        Mission mission = withStatus(MissionStatus.HIDDEN);

        mission.requireCheck();

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.HIDDEN);
    }

    @Test
    @DisplayName("원본이 다시 확인되면 CHECK_REQUIRED 미션을 ACTIVE로 복구한다.")
    void restoreFromCheck() {
        Mission mission = withStatus(MissionStatus.CHECK_REQUIRED);

        mission.restoreFromCheck();

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
    }

    @Test
    @DisplayName("운영자가 숨긴 미션은 원본이 다시 확인돼도 복구하지 않는다.")
    void restoreFromCheck_hiddenUnchanged() {
        Mission mission = withStatus(MissionStatus.HIDDEN);

        mission.restoreFromCheck();

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.HIDDEN);
    }

    @Test
    @DisplayName("기간형 미션은 종료일 다음 날부터 ENDED가 된다.")
    void endIfPeriodOver_afterEndDate() {
        Mission mission = periodMission(TODAY.minusDays(3), TODAY.minusDays(1));

        mission.endIfPeriodOver(TODAY);

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ENDED);
    }

    @Test
    @DisplayName("기간형 미션은 종료일 당일까지 ACTIVE를 유지한다.")
    void endIfPeriodOver_onEndDate() {
        Mission mission = periodMission(TODAY.minusDays(3), TODAY);

        mission.endIfPeriodOver(TODAY);

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
    }

    @Test
    @DisplayName("상시 장소형 미션은 날짜가 지나도 종료되지 않는다.")
    void endIfPeriodOver_permanent() {
        Mission mission = Mission.builder()
                .title("국립중앙박물관 관람하기").description("설명")
                .category(Category.EXHIBITION).missionType(MissionType.PERMANENT)
                .build();

        mission.endIfPeriodOver(TODAY);

        assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
    }

    private Mission periodMission(LocalDate startDate, LocalDate endDate) {
        return Mission.builder()
                .title("한강 뚜벅뚜벅 축제 방문하기").description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(startDate).endDate(endDate)
                .build();
    }

    private Mission withStatus(MissionStatus status) {
        return Mission.builder()
                .title("한강 뚜벅뚜벅 축제 방문하기").description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY)
                .status(status)
                .build();
    }
}
