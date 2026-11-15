package com.kiri.server.domain.external.client.kma;

import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class KmaResponseParser {

    private static final String SUCCESS_CODE = "00";
    private static final String NO_DATA_CODE = "03";
    private static final int SNIPPET_LENGTH = 200;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");

    private final ObjectMapper objectMapper;

    public KmaResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<KmaResDTO.ForecastItem> parse(String body) {
        if (body == null || body.isBlank()) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, "빈 응답");
        }
        JsonNode response = readTree(body).path("response");
        String resultCode = text(response.path("header"), "resultCode");
        if (resultCode == null) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, snippet(body));
        }
        if (NO_DATA_CODE.equals(resultCode)) {
            return List.of();
        }
        if (!SUCCESS_CODE.equals(resultCode)) {
            throw new ExternalException(ExternalErrorCode.ERROR_RESPONSE,
                    resultCode + " " + text(response.path("header"), "resultMsg"));
        }

        List<KmaResDTO.ForecastItem> items = new ArrayList<>();
        for (JsonNode item : response.path("body").path("items").path("item").values()) {
            items.add(new KmaResDTO.ForecastItem(
                    text(item, "category"),
                    forecastAt(item),
                    text(item, "fcstValue")));
        }
        return items;
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, snippet(body), e);
        }
    }

    private static LocalDateTime forecastAt(JsonNode item) {
        try {
            return LocalDateTime.of(
                    LocalDate.parse(text(item, "fcstDate"), DATE),
                    LocalTime.parse(text(item, "fcstTime"), TIME));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, item.toString(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String snippet(String body) {
        return body.length() <= SNIPPET_LENGTH ? body : body.substring(0, SNIPPET_LENGTH);
    }
}
