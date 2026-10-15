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

import java.time.LocalDate;
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

    @Column(name = "last_error_code", length = 30)
    private String lastErrorCode;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Builder.Default
    @Column(name = "window_full", nullable = false)
    private Boolean windowFull = false;

    @Column(name = "window_end_date")
    private LocalDate windowEndDate;

    @Column(name = "progress_date")
    private LocalDate progressDate;

    @Column(name = "progress_page")
    private Integer progressPage;

    @Column(name = "daily_call_date")
    private LocalDate dailyCallDate;

    @Builder.Default
    @Column(name = "daily_call_count", nullable = false)
    private Integer dailyCallCount = 0;

    @Column(name = "running_since")
    private LocalDateTime runningSince;

    public boolean hasOpenWindow() {
        return progressPage != null;
    }

    public void openWindow(boolean full, LocalDate from, LocalDate end) {
        this.windowFull = full;
        this.windowEndDate = end;
        this.progressDate = full ? null : from;
        this.progressPage = 1;
    }

    public void moveTo(LocalDate date, int page) {
        this.progressDate = date;
        this.progressPage = page;
    }

    public void completeWindow(String nextCursor, LocalDateTime succeededAt) {
        this.lastCursor = nextCursor;
        this.lastSuccessAt = succeededAt;
        this.lastStatus = SyncStatus.SUCCESS;
        this.lastErrorCode = null;
        this.lastError = null;
        this.windowFull = false;
        this.windowEndDate = null;
        this.progressDate = null;
        this.progressPage = null;
    }

    public void recordPause(String reasonCode, String message) {
        this.lastStatus = SyncStatus.PAUSED;
        this.lastErrorCode = reasonCode;
        this.lastError = truncate(message);
    }

    public void recordFailure(String errorCode, String error) {
        this.lastStatus = SyncStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.lastError = truncate(error);
    }

    public int callsUsedOn(LocalDate today) {
        return today.equals(dailyCallDate) ? dailyCallCount : 0;
    }

    public void addCalls(LocalDate today, int calls) {
        this.dailyCallCount = callsUsedOn(today) + calls;
        this.dailyCallDate = today;
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
