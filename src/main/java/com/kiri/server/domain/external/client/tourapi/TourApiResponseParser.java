package com.kiri.server.domain.external.client.tourapi;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TourApiResponseParser {

    private static final String SUCCESS_CODE = "0000";
    private static final int SNIPPET_LENGTH = 200;

    private static final Set<String> KEY_ERROR_CODES = Set.of("20", "30", "31", "32");
    private static final String LIMIT_EXCEEDED_CODE = "22";

    private static final Pattern XML_REASON_CODE = Pattern.compile("<returnReasonCode>\\s*(\\d+)\\s*</returnReasonCode>");
    private static final Pattern XML_AUTH_MSG = Pattern.compile("<returnAuthMsg>\\s*([^<]*)</returnAuthMsg>");

    private final ObjectMapper objectMapper;

    public TourApiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TourApiResDTO.Page parse(String body) {
        if (body == null || body.isBlank()) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, "빈 응답");
        }
        if (body.stripLeading().startsWith("<")) {
            throw fromXml(body);
        }

        JsonNode root = readTree(body);

        JsonNode gatewayHeader = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (gatewayHeader.isObject()) {
            throw gatewayError(text(gatewayHeader, "returnReasonCode"),
                    firstNonNull(text(gatewayHeader, "returnAuthMsg"), text(gatewayHeader, "errMsg")));
        }

        JsonNode response = root.path("response");
        JsonNode header = response.isObject() ? response.path("header") : root;
        String resultCode = text(header, "resultCode");
        if (resultCode == null) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, snippet(body));
        }
        if (!SUCCESS_CODE.equals(resultCode)) {
            throw new ExternalException(ExternalErrorCode.ERROR_RESPONSE,
                    resultCode + " " + text(header, "resultMsg"));
        }

        JsonNode responseBody = response.path("body");
        return TourApiResDTO.Page.builder()
                .totalCount(intValue(responseBody, "totalCount"))
                .pageNo(intValue(responseBody, "pageNo"))
                .numOfRows(intValue(responseBody, "numOfRows"))
                .items(items(responseBody))
                .build();
    }

    public static String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ExternalException(ExternalErrorCode.INVALID_RESPONSE, snippet(body), e);
        }
    }

    private static List<JsonNode> items(JsonNode responseBody) {
        JsonNode item = responseBody.path("items").path("item");
        if (item.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            item.values().forEach(items::add);
            return items;
        }
        if (item.isObject()) {
            return List.of(item);
        }
        return List.of();
    }

    private static int intValue(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static ExternalException fromXml(String body) {
        Matcher reasonCode = XML_REASON_CODE.matcher(body);
        if (reasonCode.find()) {
            Matcher authMsg = XML_AUTH_MSG.matcher(body);
            return gatewayError(reasonCode.group(1), authMsg.find() ? authMsg.group(1).trim() : null);
        }
        return new ExternalException(ExternalErrorCode.INVALID_RESPONSE, snippet(body));
    }

    private static ExternalException gatewayError(String reasonCode, String message) {
        String detail = reasonCode + " " + message;
        if (KEY_ERROR_CODES.contains(reasonCode)) {
            return new ExternalException(ExternalErrorCode.SERVICE_KEY_INVALID, detail);
        }
        if (LIMIT_EXCEEDED_CODE.equals(reasonCode)) {
            return new ExternalException(ExternalErrorCode.LIMIT_EXCEEDED, detail);
        }
        return new ExternalException(ExternalErrorCode.ERROR_RESPONSE, detail);
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static String snippet(String body) {
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= SNIPPET_LENGTH ? compact : compact.substring(0, SNIPPET_LENGTH) + "...";
    }
}
