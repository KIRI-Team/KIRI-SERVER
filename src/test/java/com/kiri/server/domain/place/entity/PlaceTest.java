package com.kiri.server.domain.place.entity;

import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import com.kiri.server.domain.place.enums.PlaceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceTest {

    @Test
    @DisplayName("기존 장소에 비어 있는 정보만 새 원본으로 채운다.")
    void fillMissingFrom_empty() {
        Place place = Place.builder().name("국립중앙박물관").placeType(PlaceType.CULTURE_FACILITY).build();

        place.fillMissingFrom(draft());

        assertThat(place.getRegionCode()).isEqualTo("11170");
        assertThat(place.getOpeningTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(place.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(place.getImageUrl()).isEqualTo("http://tong.visitkorea.or.kr/cms/resource/2.jpg");
        assertThat(place.getImageSource()).isEqualTo("한국관광공사");
    }

    @Test
    @DisplayName("이미 있는 장소 정보는 덮어쓰지 않는다.")
    void fillMissingFrom_keepsExisting() {
        Place place = Place.builder()
                .name("국립중앙박물관").placeType(PlaceType.CULTURE_FACILITY)
                .regionCode("11000")
                .openingTime(LocalTime.of(9, 0)).closingTime(LocalTime.of(20, 0))
                .imageUrl("https://example.com/1.jpg").imageSource("직접 촬영")
                .build();

        place.fillMissingFrom(draft());

        assertThat(place.getRegionCode()).isEqualTo("11000");
        assertThat(place.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(place.getImageUrl()).isEqualTo("https://example.com/1.jpg");
        assertThat(place.getImageSource()).isEqualTo("직접 촬영");
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
