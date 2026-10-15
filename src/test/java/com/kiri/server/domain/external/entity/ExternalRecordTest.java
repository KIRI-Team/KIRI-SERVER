package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.place.entity.Place;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRecordTest {

    private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 10, 13, 6, 0);
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("해시가 같으면 변경되지 않은 원본으로 판단한다.")
    void isChanged_sameHash() {
        ExternalRecord record = reviewRequired(ReviewReason.NO_LOCATION);

        assertThat(record.isChanged(HASH)).isFalse();
    }

    @Test
    @DisplayName("해시가 다르면 변경된 원본으로 판단한다.")
    void isChanged_differentHash() {
        ExternalRecord record = reviewRequired(ReviewReason.NO_LOCATION);

        assertThat(record.isChanged("b".repeat(64))).isTrue();
    }

    @Test
    @DisplayName("원본 수정 시각이 저장된 값과 같을 때만 같은 원본으로 본다.")
    void isSameOrigin() {
        ExternalRecord record = reviewRequired(ReviewReason.NO_LOCATION);
        ExternalRecord noModifiedAt = ExternalRecord.builder().contentHash(HASH).build();
        record.updateOrigin("{}", HASH, SYNCED_AT.minusDays(1), null, SYNCED_AT);

        assertThat(record.isSameOrigin(SYNCED_AT.minusDays(1))).isTrue();
        assertThat(record.isSameOrigin(SYNCED_AT)).isFalse();
        assertThat(noModifiedAt.isSameOrigin(null)).isFalse();
    }

    @Test
    @DisplayName("미션으로 가공되면 미션과 장소를 연결하고 검수 사유를 지운다.")
    void markProcessed() {
        ExternalRecord record = reviewRequired(ReviewReason.CATEGORY_UNKNOWN);
        Place place = Place.builder().name("여의도한강공원").build();
        Mission mission = Mission.builder().place(place).build();

        record.markProcessed(mission);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
        assertThat(record.getReviewReason()).isNull();
        assertThat(record.getMission()).isSameAs(mission);
        assertThat(record.getPlace()).isSameAs(place);
    }

    @Test
    @DisplayName("검수 대기로 바뀌어도 이미 연결된 미션은 유지한다.")
    void markReviewRequired_keepsMission() {
        ExternalRecord record = reviewRequired(ReviewReason.NO_LOCATION);
        Mission mission = Mission.builder().build();
        record.markProcessed(mission);

        record.markReviewRequired(ReviewReason.DUPLICATE_SUSPECT);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.REVIEW_REQUIRED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.DUPLICATE_SUSPECT);
        assertThat(record.getMission()).isSameAs(mission);
    }

    @Test
    @DisplayName("제외된 원본은 IGNORED와 제외 사유를 함께 남긴다.")
    void markIgnored() {
        ExternalRecord record = reviewRequired(ReviewReason.CATEGORY_UNKNOWN);

        record.markIgnored(ReviewReason.EXCLUDED_TYPE);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(record.getReviewReason()).isEqualTo(ReviewReason.EXCLUDED_TYPE);
        assertThat(record.isIgnored()).isTrue();
    }

    @Test
    @DisplayName("원본이 바뀌면 원본 데이터, 해시, 동기화 시각을 갱신한다.")
    void updateOrigin() {
        ExternalRecord record = reviewRequired(ReviewReason.NO_LOCATION);
        LocalDateTime nextSyncedAt = SYNCED_AT.plusHours(6);

        record.updateOrigin("{\"title\":\"변경\"}", "b".repeat(64), nextSyncedAt.minusDays(1),
                "https://example.com/2786391", nextSyncedAt);

        assertThat(record.getRawData()).isEqualTo("{\"title\":\"변경\"}");
        assertThat(record.getContentHash()).isEqualTo("b".repeat(64));
        assertThat(record.getSyncedAt()).isEqualTo(nextSyncedAt);
    }

    private ExternalRecord reviewRequired(ReviewReason reason) {
        return ExternalRecord.builder()
                .externalSource(ExternalSource.TOUR_API)
                .externalId("2786391")
                .processStatus(ProcessStatus.REVIEW_REQUIRED)
                .reviewReason(reason)
                .rawData("{\"title\":\"한강 뚜벅뚜벅 축제\"}")
                .contentHash(HASH)
                .syncedAt(SYNCED_AT)
                .build();
    }
}
