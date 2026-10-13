package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.entity.Mission;
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
    @DisplayName("미션으로 가공되면 미션을 연결하고 검수 사유를 지운다.")
    void markProcessed() {
        ExternalRecord record = reviewRequired(ReviewReason.CATEGORY_UNKNOWN);
        Mission mission = Mission.builder().build();

        record.markProcessed(mission);

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.PROCESSED);
        assertThat(record.getReviewReason()).isNull();
        assertThat(record.getMission()).isSameAs(mission);
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
    @DisplayName("미션 대상이 아닌 원본은 IGNORED로 두고 검수 사유를 지운다.")
    void markIgnored() {
        ExternalRecord record = reviewRequired(ReviewReason.CATEGORY_UNKNOWN);

        record.markIgnored();

        assertThat(record.getProcessStatus()).isEqualTo(ProcessStatus.IGNORED);
        assertThat(record.getReviewReason()).isNull();
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
