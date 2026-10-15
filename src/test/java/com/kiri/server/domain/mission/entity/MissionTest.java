package com.kiri.server.domain.mission.entity;

import com.kiri.server.domain.mission.enums.CancelSource;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.entity.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    @DisplayName("원본이 바뀌면 미션 내용과 장소를 새 가공 결과로 갱신하고 상태는 유지한다.")
    void updateContent() {
        Mission mission = withStatus(MissionStatus.HIDDEN);
        Place place = Place.builder().name("여의도한강공원").build();
        MissionDraft draft = MissionDraft.builder()
                .title("새 제목").description("새 설명")
                .category(Category.PERFORMANCE).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY.plusDays(3))
                .price(10000)
                .build();

        mission.updateContent(draft, place);

        assertThat(mission.getTitle()).isEqualTo("새 제목");
        assertThat(mission.getCategory()).isEqualTo(Category.PERFORMANCE);
        assertThat(mission.getEndDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(mission.getPrice()).isEqualTo(10000);
        assertThat(mission.getPlace()).isSameAs(place);
        assertThat(mission.getStatus()).isEqualTo(MissionStatus.HIDDEN);
    }

    @Test
    @DisplayName("재가공된 미션은 확인 필요·종료 상태에서 다시 노출하되 기간이 지났으면 종료한다.")
    void reactivate() {
        Mission checkRequired = withStatus(MissionStatus.CHECK_REQUIRED);
        Mission extended = periodMission(TODAY.minusDays(5), TODAY.plusDays(1));
        ReflectionTestUtils.setField(extended, "status", MissionStatus.ENDED);
        Mission stillOver = periodMission(TODAY.minusDays(5), TODAY.minusDays(1));
        ReflectionTestUtils.setField(stillOver, "status", MissionStatus.ENDED);

        checkRequired.reactivate(TODAY);
        extended.reactivate(TODAY);
        stillOver.reactivate(TODAY);

        assertThat(checkRequired.getStatus()).isEqualTo(MissionStatus.ACTIVE);
        assertThat(extended.getStatus()).isEqualTo(MissionStatus.ACTIVE);
        assertThat(stillOver.getStatus()).isEqualTo(MissionStatus.ENDED);
    }

    @Test
    @DisplayName("운영자가 숨기거나 취소한 미션은 재가공되어도 다시 노출하지 않는다.")
    void reactivate_operatorStatusUnchanged() {
        Mission hidden = withStatus(MissionStatus.HIDDEN);
        Mission cancelled = withStatus(MissionStatus.CANCELLED);

        hidden.reactivate(TODAY);
        cancelled.reactivate(TODAY);

        assertThat(hidden.getStatus()).isEqualTo(MissionStatus.HIDDEN);
        assertThat(cancelled.getStatus()).isEqualTo(MissionStatus.CANCELLED);
    }

    @Test
    @DisplayName("운영자가 수정한 제목은 원본이 바뀌어도 유지하고 나머지 내용만 갱신한다.")
    void updateContent_keepsEditedTitle() {
        Mission mission = Mission.builder()
                .title("운영자가 고친 제목").description("설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY)
                .titleEdited(true)
                .build();
        MissionDraft draft = MissionDraft.builder()
                .title("자동 제목").description("새 설명")
                .category(Category.POPUP).missionType(MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY.plusDays(1))
                .build();

        mission.updateContent(draft, Place.builder().name("장소").build());

        assertThat(mission.getTitle()).isEqualTo("운영자가 고친 제목");
        assertThat(mission.getDescription()).isEqualTo("새 설명");
        assertThat(mission.getEndDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    @DisplayName("외부 행사 취소는 취소 주체와 사유를 남기고, 운영자 상태는 바꾸지 않는다.")
    void cancelByExternal() {
        Mission active = withStatus(MissionStatus.ACTIVE);
        Mission hidden = withStatus(MissionStatus.HIDDEN);

        active.cancelByExternal("원본 제목에 취소 표시");
        hidden.cancelByExternal("원본 제목에 취소 표시");

        assertThat(active.getStatus()).isEqualTo(MissionStatus.CANCELLED);
        assertThat(active.getCancelSource()).isEqualTo(CancelSource.EXTERNAL);
        assertThat(active.getCancelReason()).isEqualTo("원본 제목에 취소 표시");
        assertThat(hidden.getStatus()).isEqualTo(MissionStatus.HIDDEN);
        assertThat(hidden.getCancelSource()).isNull();
    }

    @Test
    @DisplayName("운영자가 필수 활동을 수정하면 자동 재생성 대상에서 빠진다.")
    void isChecklistLocked() {
        Mission edited = Mission.builder().checklistEdited(true).build();
        Mission auto = Mission.builder().build();

        assertThat(edited.isChecklistLocked()).isTrue();
        assertThat(auto.isChecklistLocked()).isFalse();
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
