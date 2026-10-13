package com.kiri.server.domain.external.client.tourapi.dto;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TourApiResDTO {

    @Builder
    public record Page(
            int totalCount,
            int pageNo,
            int numOfRows,
            List<JsonNode> items
    ) {

        public boolean hasNext() {
            return numOfRows > 0 && (long) pageNo * numOfRows < totalCount;
        }

        public JsonNode firstItemOrNull() {
            return items.isEmpty() ? null : items.getFirst();
        }
    }

    @Builder
    public record SyncItem(
            String contentId,
            String contentTypeId,
            String title,
            String address,
            String addressDetail,
            BigDecimal longitude,
            BigDecimal latitude,
            String imageUrl,
            String copyrightType,
            String regionCode,
            String sigunguCode,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3,
            boolean shown,
            LocalDateTime modifiedAt,
            JsonNode raw
    ) {
    }
}
