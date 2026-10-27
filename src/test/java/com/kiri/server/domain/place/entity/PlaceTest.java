package com.kiri.server.domain.place.entity;

import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.enums.PlaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceTest {

    @Test
    @DisplayName("기존 장소에 비어 있는 지역·운영시간만 새 원본으로 채운다.")
    void fillMissingFrom_empty() {
        Place place = Place.builder().name("국립중앙박물관").placeType(PlaceType.CULTURE_FACILITY).build();

        place.fillMissingFrom(draft());

        assertThat(place.getRegionCode()).isEqualTo("11170");
        assertThat(place.getOpeningTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(place.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("이미 있는 지역·운영시간은 덮어쓰지 않는다.")
    void fillMissingFrom_keepsExisting() {
        Place place = Place.builder()
                .name("국립중앙박물관").placeType(PlaceType.CULTURE_FACILITY)
                .regionCode("11000")
                .openingTime(LocalTime.of(9, 0)).closingTime(LocalTime.of(20, 0))
                .build();

        place.fillMissingFrom(draft());

        assertThat(place.getRegionCode()).isEqualTo("11000");
        assertThat(place.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("이미지는 최신 원본의 이용 조건을 따라 바꾸고, 사용할 수 없게 되면 비운다.")
    void refreshImage() {
        Place place = Place.builder()
                .name("국립중앙박물관").placeType(PlaceType.CULTURE_FACILITY)
                .imageUrl("http://tong.visitkorea.or.kr/cms/resource/1.jpg")
                .imageSource("한국관광공사 (공공누리 제1유형)")
                .build();

        place.refreshImage("http://tong.visitkorea.or.kr/cms/resource/2.jpg", "한국관광공사 (공공누리 제3유형·변경금지)");

        assertThat(place.getImageUrl()).isEqualTo("http://tong.visitkorea.or.kr/cms/resource/2.jpg");
        assertThat(place.getImageSource()).isEqualTo("한국관광공사 (공공누리 제3유형·변경금지)");

        place.refreshImage(null, "한국관광공사 (공공누리 제1유형)");

        assertThat(place.getImageUrl()).isNull();
        assertThat(place.getImageSource()).isNull();
    }

    private MissionDraft.PlaceDraft draft() {
        return MissionDraft.PlaceDraft.builder()
                .name("국립중앙박물관")
                .placeType(PlaceType.CULTURE_FACILITY)
                .regionCode("11170")
                .openingTime(LocalTime.of(10, 0))
                .closingTime(LocalTime.of(18, 0))
                .imageUrl("http://tong.visitkorea.or.kr/cms/resource/2.jpg")
                .imageSource("한국관광공사")
                .build();
    }
}
