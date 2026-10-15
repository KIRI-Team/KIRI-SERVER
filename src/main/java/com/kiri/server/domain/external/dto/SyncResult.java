package com.kiri.server.domain.external.dto;

import lombok.Builder;

@Builder
public record SyncResult(
        boolean completed,
        int processed,
        int unchanged,
        int hidden,
        int skipped,
        int failed,
        int calls,
        String error
) {
}
