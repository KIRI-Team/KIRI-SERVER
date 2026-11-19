package com.kiri.server.domain.external.client.kma.dto;

import java.time.LocalDateTime;

public class KmaResDTO {

    // category: TMP(기온), POP(강수확률), PTY(강수형태), TMN/TMX(최저/최고) 등
    public record ForecastItem(
            String category,
            LocalDateTime forecastAt,
            String value
    ) {
    }
}
