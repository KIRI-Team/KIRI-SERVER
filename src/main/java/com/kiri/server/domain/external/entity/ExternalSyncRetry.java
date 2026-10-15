package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "external_sync_retry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_sync_retry_source_id",
                columnNames = {"external_source", "external_id"}
        )
)
public class ExternalSyncRetry extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_sync_retry_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source", length = 20, nullable = false)
    private ExternalSource externalSource;

    @Column(name = "external_id", length = 100, nullable = false)
    private String externalId;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "raw_item", columnDefinition = "jsonb", nullable = false)
    private String rawItem;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public void recordFailure(String rawItem, String error) {
        this.rawItem = rawItem;
        this.attemptCount = attemptCount + 1;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
