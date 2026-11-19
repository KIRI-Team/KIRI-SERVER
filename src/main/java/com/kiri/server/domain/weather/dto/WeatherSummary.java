package com.kiri.server.domain.weather.dto;

// 일정 구간의 예보 요약
public record WeatherSummary(
        double maxTemperature,
        double minTemperature,
        int maxPrecipitationProbability,
        boolean precipitation
) {
}
