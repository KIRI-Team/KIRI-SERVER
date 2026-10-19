package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.dto.MissionReqDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.enums.PlaceType;
import com.kiri.server.domain.place.repository.PlaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MissionFeedRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Autowired
    private MissionFeedRepository missionFeedRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private EntityManager entityManager;

    private Long festival;
    private Long museum;
    private Long nightBar;
    private Long nightOpening;
    private List<Long> created;

    @BeforeEach
    void setUp() {
        Place seoulForest = place("서울숲", "11200", "37.5444000", "127.0374000", null, null);
        Place gallery = place("국립현대미술관 서울", "11110", "37.5786000", "126.9800000", LocalTime.of(10, 0), LocalTime.of(18, 0));
        Place pub = place("망원 포차", "11440", "37.5560000", "126.9230000", LocalTime.of(18, 0), LocalTime.of(2, 0));

        festival = save(builder(seoulForest, Category.POPUP, MissionType.PERIOD)
                .startDate(TODAY.minusDays(2)).endDate(TODAY.plusDays(6)));
        museum = save(builder(gallery, Category.EXHIBITION, MissionType.PERMANENT).price(0));
        nightBar = save(builder(pub, Category.FOOD_CAFE, MissionType.PERMANENT).price(15000));
        Long ended = save(builder(seoulForest, Category.POPUP, MissionType.PERIOD)
                .startDate(TODAY.minusDays(20)).endDate(TODAY.minusDays(1)).price(0));
        Long checking = save(builder(gallery, Category.EXHIBITION, MissionType.PERMANENT)
                .status(MissionStatus.CHECK_REQUIRED));
        nightOpening = save(builder(gallery, Category.EXHIBITION, MissionType.PERIOD)
                .startDate(TODAY).endDate(TODAY.plusDays(1)).price(30000)
                .openingTime(LocalTime.of(19, 0)).closingTime(LocalTime.of(22, 0)));
        created = List.of(festival, museum, nightBar, ended, checking, nightOpening);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("공개 중이고 기간이 끝나지 않은 미션만 신규순으로 조회한다.")
    void visibleOnly() {
        assertThat(find(condition().build())).containsExactly(nightOpening, nightBar, museum, festival);
    }

    @Test
    @DisplayName("카테고리와 지역 코드 앞자리로 거른다.")
    void categoryAndRegion() {
        assertThat(find(condition().category(Category.EXHIBITION).build())).containsExactly(nightOpening, museum);
        assertThat(find(condition().regionCode("11200").build())).containsExactly(festival);
        assertThat(find(condition().regionCode("11").build())).containsExactly(nightOpening, nightBar, museum, festival);
    }

    @Test
    @DisplayName("기준 좌표에서 거리 안에 있는 장소의 미션만 남기고, 좌표가 없으면 거리 조건을 무시한다.")
    void distance() {
        assertThat(find(condition().latitude(37.5446).longitude(127.0557).distance(3).build()))
                .containsExactly(festival);
        assertThat(find(condition().distance(3).build())).hasSize(4);
    }

    @Test
    @DisplayName("날짜 조건은 기간형은 기간 안일 때만, 상시 장소형은 항상 포함한다.")
    void date() {
        assertThat(find(condition().date(TODAY.plusDays(7)).build())).containsExactly(nightBar, museum);
    }

    @Test
    @DisplayName("시간 조건은 미션 운영시간이 없으면 장소 운영시간을 쓰고, 운영시간 정보가 없으면 포함한다.")
    void time() {
        assertThat(find(condition().time(LocalTime.of(14, 0)).build())).containsExactly(museum, festival);
        assertThat(find(condition().time(LocalTime.of(20, 0)).build())).containsExactly(nightOpening, nightBar, festival);
        assertThat(find(condition().time(LocalTime.of(1, 0)).build())).containsExactly(nightBar, festival);
    }

    @Test
    @DisplayName("가격 조건은 정보가 없는 미션을 포함하고, 무료만 보기가 최대 가격보다 우선한다.")
    void price() {
        assertThat(find(condition().isFree(true).maxPrice(100000).build())).containsExactly(museum, festival);
        assertThat(find(condition().maxPrice(20000).build())).containsExactly(nightBar, museum, festival);
    }

    @Test
    @DisplayName("커서보다 작은 ID부터 limit만큼 조회한다.")
    void cursor() {
        List<Long> ids = missionFeedRepository.findFeedIds(condition().cursor(nightBar).build(), TODAY, 1);

        assertThat(ids).containsExactly(museum);
    }

    private List<Long> find(MissionReqDTO.FeedCondition condition) {
        return missionFeedRepository.findFeedIds(condition, TODAY, 1000).stream()
                .filter(created::contains)
                .toList();
    }

    private MissionReqDTO.FeedCondition.FeedConditionBuilder condition() {
        return MissionReqDTO.FeedCondition.builder();
    }

    private Place place(String name, String regionCode, String latitude, String longitude,
                        LocalTime openingTime, LocalTime closingTime) {
        return placeRepository.save(Place.builder()
                .name(name)
                .placeType(PlaceType.TOURIST_SPOT)
                .regionCode(regionCode)
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .openingTime(openingTime)
                .closingTime(closingTime)
                .build());
    }

    private Mission.MissionBuilder builder(Place place, Category category, MissionType missionType) {
        return Mission.builder()
                .place(place)
                .title(place.getName() + " 방문하기")
                .description("설명")
                .category(category)
                .missionType(missionType);
    }

    private Long save(Mission.MissionBuilder builder) {
        return missionRepository.save(builder.build()).getId();
    }
}
