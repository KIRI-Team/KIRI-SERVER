package com.kiri.server.domain.external.client.tourapi;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TourApiClient {

    private static final String MOBILE_OS = "ETC";
    private static final String RESPONSE_TYPE = "json";
    private static final String ARRANGE_BY_MODIFIED = "C";
    private static final int DETAIL_ROWS = 50;

    private final RestClient tourApiRestClient;
    private final TourApiProperties properties;
    private final TourApiResponseParser parser;

    public TourApiClient(RestClient tourApiRestClient, TourApiProperties properties, ObjectMapper objectMapper) {
        this.tourApiRestClient = tourApiRestClient;
        this.properties = properties;
        this.parser = new TourApiResponseParser(objectMapper);
    }

    public TourApiResDTO.Page fetchSyncList(int pageNo, String modifiedDate) {
        return fetchSyncList(pageNo, modifiedDate, null, properties.numOfRows());
    }

    public TourApiResDTO.Page fetchSyncList(int pageNo, String modifiedDate, String contentTypeId, int numOfRows) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("arrange", ARRANGE_BY_MODIFIED);
        putIfPresent(params, "lDongRegnCd", properties.regionCode());
        putIfPresent(params, "contentTypeId", contentTypeId);
        putIfPresent(params, "modifiedtime", modifiedDate);
        return call(TourApiOperation.AREA_BASED_SYNC_LIST, params);
    }

    public TourApiResDTO.Page fetchDetailCommon(String contentId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("contentId", contentId);
        return call(TourApiOperation.DETAIL_COMMON, params);
    }

    public TourApiResDTO.Page fetchDetailIntro(String contentId, String contentTypeId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("contentId", contentId);
        params.put("contentTypeId", contentTypeId);
        return call(TourApiOperation.DETAIL_INTRO, params);
    }

    public TourApiResDTO.Page fetchDetailInfo(String contentId, String contentTypeId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("contentId", contentId);
        params.put("contentTypeId", contentTypeId);
        params.put("numOfRows", String.valueOf(DETAIL_ROWS));
        params.put("pageNo", "1");
        return call(TourApiOperation.DETAIL_INFO, params);
    }

    private TourApiResDTO.Page call(TourApiOperation operation, Map<String, String> params) {
        if (!properties.hasServiceKey()) {
            throw new ExternalException(ExternalErrorCode.SERVICE_KEY_MISSING);
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("serviceKey", properties.serviceKey());
        query.put("MobileOS", MOBILE_OS);
        query.put("MobileApp", properties.mobileApp());
        query.put("_type", RESPONSE_TYPE);
        query.putAll(params);

        Response response;
        try {
            response = tourApiRestClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, operation, query))
                    .exchange((request, clientResponse) -> new Response(
                            clientResponse.getStatusCode(),
                            new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new ExternalException(ExternalErrorCode.CALL_FAILED, operation.getPath() + " " + e.getMessage(), e);
        }

        if (!response.status().is2xxSuccessful()) {
            throw failedResponse(operation, response);
        }
        return parser.parse(response.body());
    }

    private ExternalException failedResponse(TourApiOperation operation, Response response) {
        try {
            parser.parse(response.body());
        } catch (ExternalException e) {
            if (e.getErrorCode() != ExternalErrorCode.INVALID_RESPONSE) {
                return e;
            }
        }
        return new ExternalException(ExternalErrorCode.CALL_FAILED,
                operation.getPath() + " HTTP " + response.status().value());
    }

    private static URI buildUri(UriBuilder uriBuilder, TourApiOperation operation, Map<String, String> query) {
        uriBuilder.path(operation.getPath());
        query.keySet().forEach(name -> uriBuilder.queryParam(name, "{" + name + "}"));
        return uriBuilder.build(query);
    }

    private static void putIfPresent(Map<String, String> params, String name, String value) {
        if (value != null && !value.isBlank()) {
            params.put(name, value);
        }
    }

    private record Response(HttpStatusCode status, String body) {
    }
}
