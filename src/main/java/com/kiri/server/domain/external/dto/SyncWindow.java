package com.kiri.server.domain.external.dto;

import java.time.LocalDate;

public record SyncWindow(
        boolean full,
        LocalDate fromDate,
        LocalDate endDate,
        int startPage,
        boolean resumed,
        int callsUsedToday
) {
}
