package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "external_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_record_source_id",
                columnNames = {"external_source", "external_id"}
        )
)
public class ExternalRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_record_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source", length = 20, nullable = false)
    private ExternalSource externalSource;

    @Column(name = "external_id", length = 100, nullable = false)
    private String externalId;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_status", length = 20, nullable = false)
    private ProcessStatus processStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", length = 20)
    private ReviewReason reviewReason;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "origin_modified_at")
    private LocalDateTime originModifiedAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public boolean isSameOrigin(LocalDateTime modifiedAt) {
        return originModifiedAt != null && originModifiedAt.equals(modifiedAt);
    }

    public boolean isChanged(String newContentHash) {
        return !contentHash.equals(newContentHash);
    }

    public void updateOrigin(String rawData, String contentHash, LocalDateTime originModifiedAt,
                             String sourceUrl, LocalDateTime syncedAt) {
        this.rawData = rawData;
        this.contentHash = contentHash;
        this.originModifiedAt = originModifiedAt;
        this.sourceUrl = sourceUrl;
        this.syncedAt = syncedAt;
    }

    public void markSynced(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    public void markProcessed(Mission mission) {
        this.mission = mission;
        this.place = mission.getPlace();
        this.processStatus = ProcessStatus.PROCESSED;
        this.reviewReason = null;
    }

    public void markReviewRequired(ReviewReason reviewReason) {
        this.processStatus = ProcessStatus.REVIEW_REQUIRED;
        this.reviewReason = reviewReason;
    }

    public void markIgnored() {
        this.processStatus = ProcessStatus.IGNORED;
        this.reviewReason = null;
    }
}
