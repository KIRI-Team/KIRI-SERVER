package com.kiri.server.domain.external.client.kma;

import com.kiri.server.domain.external.client.kma.dto.KmaResDTO;
import com.kiri.server.domain.external.exception.ExternalException;
import com.kiri.server.domain.external.exception.code.ExternalErrorCode;
import com.kiri.server.global.config.RestClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KmaForecastClientTest {

    private static final String BASE_URL = "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0";
    private static final String AUTH_KEY = "testAuthKey123";
    private static final KmaGrid SEOUL = new KmaGrid(60, 127);
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 15, 5, 0);

    private static final String FORECAST = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
              "body":{"dataType":"JSON","items":{"item":[
                {"baseDate":"20261115","baseTime":"0500","category":"TMP","fcstDate":"20261115","fcstTime":"0600","fcstValue":"3","nx":60,"ny":127},
                {"baseDate":"20261115","baseTime":"0500","category":"POP","fcstDate":"20261115","fcstTime":"0600","fcstValue":"60","nx":60,"ny":127}
              ]},"pageNo":1,"numOfRows":2000,"totalCount":2}}}
            """;

    private static final String NO_DATA = """
            {"response":{"header":{"resultCode":"03","resultMsg":"NO_DATA"}}}
            """;

    private static final String PROVIDER_ERROR = """
            {"response":{"header":{"resultCode":"10","resultMsg":"INVALID_REQUEST_PARAMETER_ERROR"}}}
            """;

    @Test
    @DisplayName("발표시각과 격자, 인증키를 담아 단기예보를 요청한다.")
    void fetchVillageForecast_requestParams() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(requestTo(startsWith(BASE_URL + "/getVilageFcst?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("authKey", AUTH_KEY))
                .andExpect(queryParam("dataType", "JSON"))
                .andExpect(queryParam("base_date", "20261115"))
                .andExpect(queryParam("base_time", "0500"))
                .andExpect(queryParam("nx", "60"))
                .andExpect(queryParam("ny", "127"))
                .andRespond(withSuccess(FORECAST, MediaType.APPLICATION_JSON));

        fixture.client.fetchVillageForecast(SEOUL, BASE_TIME);

        fixture.server.verify();
    }

    @Test
    @DisplayName("예보 항목을 항목 코드, 예보 시각, 값으로 변환한다.")
    void fetchVillageForecast_parsesItems() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(anything()).andRespond(withSuccess(FORECAST, MediaType.APPLICATION_JSON));

        List<KmaResDTO.ForecastItem> items = fixture.client.fetchVillageForecast(SEOUL, BASE_TIME);

        LocalDateTime forecastAt = LocalDateTime.of(2026, 11, 15, 6, 0);
        assertThat(items).containsExactly(
                new KmaResDTO.ForecastItem("TMP", forecastAt, "3"),
                new KmaResDTO.ForecastItem("POP", forecastAt, "60"));
    }

    @Test
    @DisplayName("데이터 없음(03) 응답이면 빈 목록을 반환한다.")
    void fetchVillageForecast_noData() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(anything()).andRespond(withSuccess(NO_DATA, MediaType.APPLICATION_JSON));

        assertThat(fixture.client.fetchVillageForecast(SEOUL, BASE_TIME)).isEmpty();
    }

    @Test
    @DisplayName("인증키가 비어 있으면 호출하지 않고 SERVICE_KEY_MISSING 예외가 발생한다.")
    void authKey_missing() {
        Fixture fixture = fixture(" ");
        fixture.server.expect(never(), anything());

        assertThatThrownBy(() -> fixture.client.fetchVillageForecast(SEOUL, BASE_TIME))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.SERVICE_KEY_MISSING);

        fixture.server.verify();
    }

    @Test
    @DisplayName("401·403 응답이면 SERVICE_KEY_INVALID 예외가 발생한다.")
    void authKey_invalid() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> fixture.client.fetchVillageForecast(SEOUL, BASE_TIME))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.SERVICE_KEY_INVALID);
    }

    @Test
    @DisplayName("그 밖의 HTTP 오류면 CALL_FAILED 예외가 발생한다.")
    void httpError() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> fixture.client.fetchVillageForecast(SEOUL, BASE_TIME))
                .isInstanceOf(ExternalException.class)
                .hasMessageContaining("503")
                .extracting("errorCode").isEqualTo(ExternalErrorCode.CALL_FAILED);
    }

    @Test
    @DisplayName("resultCode가 00·03이 아니면 ERROR_RESPONSE 예외가 발생한다.")
    void providerError() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(anything()).andRespond(withSuccess(PROVIDER_ERROR, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.fetchVillageForecast(SEOUL, BASE_TIME))
                .isInstanceOf(ExternalException.class)
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR")
                .extracting("errorCode").isEqualTo(ExternalErrorCode.ERROR_RESPONSE);
    }

    @Test
    @DisplayName("해석할 수 없는 응답이면 INVALID_RESPONSE 예외가 발생한다.")
    void invalidBody() {
        Fixture fixture = fixture(AUTH_KEY);
        fixture.server.expect(anything()).andRespond(withSuccess("<html>점검 중</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> fixture.client.fetchVillageForecast(SEOUL, BASE_TIME))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.INVALID_RESPONSE);
    }

    private Fixture fixture(String authKey) {
        KmaProperties properties = new KmaProperties(BASE_URL, authKey, Duration.ofSeconds(2), Duration.ofSeconds(3));
        RestClient.Builder builder = RestClient.builder()
                .uriBuilderFactory(RestClientConfig.strictUriBuilderFactory(BASE_URL));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KmaForecastClient client = new KmaForecastClient(builder.build(), properties, new JsonMapper());
        return new Fixture(client, server);
    }

    private record Fixture(KmaForecastClient client, MockRestServiceServer server) {
    }
}
