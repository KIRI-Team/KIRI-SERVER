package com.kiri.server.domain.external.client.kma;

import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class KmaForecastClient {

    private static final String VILLAGE_FORECAST_PATH = "/getVilageFcst";
    // 한 발표분 전체(최대 그글피 자정까지, 시간당 12개 항목)
    private static final int NUM_OF_ROWS = 2000;
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");

    private final RestClient kmaRestClient;
    private final KmaProperties properties;
    private final KmaResponseParser parser;

    public KmaForecastClient(RestClient kmaRestClient, KmaProperties properties, ObjectMapper objectMapper) {
        this.kmaRestClient = kmaRestClient;
        this.properties = properties;
        this.parser = new KmaResponseParser(objectMapper);
    }

    public List<KmaResDTO.ForecastItem> fetchVillageForecast(KmaGrid grid, LocalDateTime baseTime) {
        if (!properties.hasAuthKey()) {
            throw new ExternalException(ExternalErrorCode.SERVICE_KEY_MISSING);
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("authKey", properties.authKey());
        query.put("dataType", "JSON");
        query.put("pageNo", "1");
        query.put("numOfRows", String.valueOf(NUM_OF_ROWS));
        query.put("base_date", baseTime.format(BASE_DATE));
        query.put("base_time", baseTime.format(BASE_TIME));
        query.put("nx", String.valueOf(grid.nx()));
        query.put("ny", String.valueOf(grid.ny()));

        Response response;
        try {
            response = kmaRestClient.get()
                    .uri(uriBuilder -> buildUri(uriBuilder, query))
                    .exchange((request, clientResponse) -> new Response(
                            clientResponse.getStatusCode(),
                            new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new ExternalException(ExternalErrorCode.CALL_FAILED, VILLAGE_FORECAST_PATH + " " + e.getMessage(), e);
        }

        if (response.status().value() == 401 || response.status().value() == 403) {
            throw new ExternalException(ExternalErrorCode.SERVICE_KEY_INVALID,
                    VILLAGE_FORECAST_PATH + " HTTP " + response.status().value());
        }
        if (!response.status().is2xxSuccessful()) {
            throw new ExternalException(ExternalErrorCode.CALL_FAILED,
                    VILLAGE_FORECAST_PATH + " HTTP " + response.status().value());
        }
        return parser.parse(response.body());
    }

    private static URI buildUri(UriBuilder uriBuilder, Map<String, String> query) {
        uriBuilder.path(VILLAGE_FORECAST_PATH);
        query.keySet().forEach(name -> uriBuilder.queryParam(name, "{" + name + "}"));
        return uriBuilder.build(query);
    }

    private record Response(HttpStatusCode status, String body) {
    }
}
