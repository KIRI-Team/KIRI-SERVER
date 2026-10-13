package com.kiri.server.domain.external.entity;

import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.SyncStatus;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "external_sync_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_external_sync_state_source",
                columnNames = "external_source"
        )
)
public class ExternalSyncState extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_sync_state_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source", length = 20, nullable = false)
    private ExternalSource externalSource;

    @Column(name = "last_cursor", length = 255)
    private String lastCursor;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 20)
    private SyncStatus lastStatus;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    public void recordSuccess(String nextCursor, LocalDateTime succeededAt) {
        this.lastCursor = nextCursor;
        this.lastSuccessAt = succeededAt;
        this.lastStatus = SyncStatus.SUCCESS;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.lastStatus = SyncStatus.FAILED;
        this.lastError = truncate(error);
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
