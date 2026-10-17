package com.kiri.server.domain.external.dto;

import com.kiri.server.domain.external.enums.ExternalSource;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record NormalizedContent(
        ExternalSource source,
        String externalId,
        String contentTypeId,
        String title,
        String overview,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String regionCode,
        String imageUrl,
        String imageSource,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        LocalDate startDate,
        LocalDate endDate,
        String venueName,
        String usageTimeText,
        String feeText,
        String durationText,
        List<String> programTexts,
        List<String> menuNames
) {

    public NormalizedContent {
        programTexts = programTexts == null ? List.of() : List.copyOf(programTexts);
        menuNames = menuNames == null ? List.of() : List.copyOf(menuNames);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public String placeName() {
        return venueName != null && !venueName.isBlank() ? venueName : title;
    }
}
