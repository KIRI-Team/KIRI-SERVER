package com.kiri.server.domain.mission.generator.dto;

import com.kiri.server.domain.external.enums.ReviewReason;

public sealed interface DraftResult {

    record Ready(MissionDraft draft) implements DraftResult {
    }

    record ReviewRequired(ReviewReason reason) implements DraftResult {
    }

    record Ignored(ReviewReason reason) implements DraftResult {
    }
}
