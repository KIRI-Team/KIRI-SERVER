package com.kiri.server.domain.external.client.tourapi;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
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

class TourApiClientTest {

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    private static final String SERVICE_KEY = "testServiceKey123";

    private static final String SYNC_LIST_TWO_ITEMS = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
              "body":{"items":{"item":[
                {"contentid":"2786391","contenttypeid":"15","title":"한강 뚜벅뚜벅 축제","showflag":"1"},
                {"contentid":"126508","contenttypeid":"12","title":"경복궁","showflag":"0"}
              ]},"numOfRows":100,"pageNo":1,"totalCount":250}}}
            """;

    private static final String SINGLE_ITEM_OBJECT = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
              "body":{"items":{"item":{"contentid":"2786391","contenttypeid":"15","overview":"개요"}},
                "numOfRows":10,"pageNo":1,"totalCount":1}}}
            """;

    private static final String EMPTY_ITEMS = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
              "body":{"items":"","numOfRows":100,"pageNo":1,"totalCount":0}}}
            """;

    private static final String GATEWAY_KEY_NOT_REGISTERED = """
            {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
              "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
              "returnAuthMsg":"등록되지 않은 서비스키",
              "returnReasonCode":"30"}}}
            """;

    private static final String GATEWAY_LIMIT_EXCEEDED_XML = """
            <OpenAPI_ServiceResponse><cmmMsgHeader>
              <errMsg>SERVICE ERROR</errMsg>
              <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
              <returnReasonCode>22</returnReasonCode>
            </cmmMsgHeader></OpenAPI_ServiceResponse>
            """;

    private static final String PROVIDER_ERROR = """
            {"responseTime":"2026-10-13T06:00:00","resultCode":"10","resultMsg":"INVALID_REQUEST_PARAMETER_ERROR"}
            """;

    @Test
    @DisplayName("동기화 목록 요청에 필수 파라미터와 지역·수정일 조건을 담는다.")
    void fetchSyncList_requestParams() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(requestTo(startsWith(BASE_URL + "/areaBasedSyncList2?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("serviceKey", SERVICE_KEY))
                .andExpect(queryParam("MobileOS", "ETC"))
                .andExpect(queryParam("MobileApp", "KIRI"))
                .andExpect(queryParam("_type", "json"))
                .andExpect(queryParam("numOfRows", "100"))
                .andExpect(queryParam("pageNo", "2"))
                .andExpect(queryParam("arrange", "C"))
                .andExpect(queryParam("lDongRegnCd", "11"))
                .andExpect(queryParam("modifiedtime", "20261012"))
                .andRespond(withSuccess(SYNC_LIST_TWO_ITEMS, MediaType.APPLICATION_JSON));

        fixture.client.fetchSyncList(2, "20261012");

        fixture.server.verify();
    }

    @Test
    @DisplayName("지역 코드와 수정일이 없으면 해당 파라미터를 보내지 않는다.")
    void fetchSyncList_withoutOptionalParams() {
        Fixture fixture = fixture(SERVICE_KEY, "");
        fixture.server.expect(requestTo(startsWith(BASE_URL + "/areaBasedSyncList2?")))
                .andExpect(request -> {
                    String query = request.getURI().getRawQuery();
                    assertThat(query).doesNotContain("lDongRegnCd").doesNotContain("modifiedtime");
                })
                .andRespond(withSuccess(SYNC_LIST_TWO_ITEMS, MediaType.APPLICATION_JSON));

        fixture.client.fetchSyncList(1, null);

        fixture.server.verify();
    }

    @Test
    @DisplayName("목록 응답의 항목과 페이지 정보를 읽는다.")
    void fetchSyncList_parsesItems() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withSuccess(SYNC_LIST_TWO_ITEMS, MediaType.APPLICATION_JSON));

        TourApiResDTO.Page page = fixture.client.fetchSyncList(1, null);

        assertThat(page.totalCount()).isEqualTo(250);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.numOfRows()).isEqualTo(100);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(1).path("title").asString()).isEqualTo("경복궁");
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("결과가 한 건이면 객체로 온 item도 목록으로 읽는다.")
    void fetchDetailCommon_singleItemObject() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(requestTo(startsWith(BASE_URL + "/detailCommon2?")))
                .andExpect(queryParam("contentId", "2786391"))
                .andRespond(withSuccess(SINGLE_ITEM_OBJECT, MediaType.APPLICATION_JSON));

        TourApiResDTO.Page page = fixture.client.fetchDetailCommon("2786391");

        assertThat(page.items()).hasSize(1);
        assertThat(page.firstItemOrNull().path("overview").asString()).isEqualTo("개요");
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("결과가 없어 items가 빈 문자열로 오면 빈 목록을 반환한다.")
    void fetchSyncList_emptyItems() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

        TourApiResDTO.Page page = fixture.client.fetchSyncList(1, "20261012");

        assertThat(page.items()).isEmpty();
        assertThat(page.firstItemOrNull()).isNull();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("소개·반복정보 요청에 콘텐츠 ID와 타입을 담는다.")
    void fetchDetailIntroAndInfo_requestParams() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(requestTo(startsWith(BASE_URL + "/detailIntro2?")))
                .andExpect(queryParam("contentId", "2786391"))
                .andExpect(queryParam("contentTypeId", "15"))
                .andRespond(withSuccess(SINGLE_ITEM_OBJECT, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(startsWith(BASE_URL + "/detailInfo2?")))
                .andExpect(queryParam("contentId", "2786391"))
                .andExpect(queryParam("contentTypeId", "15"))
                .andExpect(queryParam("numOfRows", "50"))
                .andRespond(withSuccess(SINGLE_ITEM_OBJECT, MediaType.APPLICATION_JSON));

        fixture.client.fetchDetailIntro("2786391", "15");
        fixture.client.fetchDetailInfo("2786391", "15");

        fixture.server.verify();
    }

    @Test
    @DisplayName("인증키의 +, /, = 는 인코딩해서 보낸다.")
    void serviceKey_isStrictlyEncoded() {
        Fixture fixture = fixture("ab+c/d==", "11");
        fixture.server.expect(anything())
                .andExpect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("serviceKey=ab%2Bc%2Fd%3D%3D"))
                .andRespond(withSuccess(EMPTY_ITEMS, MediaType.APPLICATION_JSON));

        fixture.client.fetchSyncList(1, null);

        fixture.server.verify();
    }

    @Test
    @DisplayName("인증키가 비어 있으면 호출하지 않고 SERVICE_KEY_MISSING 예외가 발생한다.")
    void serviceKey_missing() {
        Fixture fixture = fixture(" ", "11");
        fixture.server.expect(never(), anything());

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.SERVICE_KEY_MISSING);

        fixture.server.verify();
    }

    @Test
    @DisplayName("등록되지 않은 인증키 응답이면 SERVICE_KEY_INVALID 예외가 발생한다.")
    void gatewayKeyError() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(GATEWAY_KEY_NOT_REGISTERED));

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .hasMessageContaining("30")
                .extracting("errorCode").isEqualTo(ExternalErrorCode.SERVICE_KEY_INVALID);
    }

    @Test
    @DisplayName("200으로 온 게이트웨이 오류도 인증키 오류로 해석한다.")
    void gatewayKeyError_withOkStatus() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withSuccess(GATEWAY_KEY_NOT_REGISTERED, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.SERVICE_KEY_INVALID);
    }

    @Test
    @DisplayName("XML로 온 호출 한도 초과 응답이면 LIMIT_EXCEEDED 예외가 발생한다.")
    void gatewayLimitExceeded_xml() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withSuccess(GATEWAY_LIMIT_EXCEEDED_XML, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("resultCode가 0000이 아니면 ERROR_RESPONSE 예외가 발생한다.")
    void providerError() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withSuccess(PROVIDER_ERROR, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR")
                .extracting("errorCode").isEqualTo(ExternalErrorCode.ERROR_RESPONSE);
    }

    @Test
    @DisplayName("해석할 수 없는 응답이면 INVALID_RESPONSE 예외가 발생한다.")
    void invalidBody() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withSuccess("{\"unexpected\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .extracting("errorCode").isEqualTo(ExternalErrorCode.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("본문을 해석할 수 없는 HTTP 오류면 CALL_FAILED 예외가 발생한다.")
    void httpErrorWithoutReadableBody() {
        Fixture fixture = fixture(SERVICE_KEY, "11");
        fixture.server.expect(anything())
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html><body>Service Unavailable</body></html>"));

        assertThatThrownBy(() -> fixture.client.fetchSyncList(1, null))
                .isInstanceOf(ExternalException.class)
                .hasMessageContaining("503")
                .extracting("errorCode").isEqualTo(ExternalErrorCode.CALL_FAILED);
    }

    private Fixture fixture(String serviceKey, String regionCode) {
        TourApiProperties properties = new TourApiProperties(
                BASE_URL, serviceKey, "KIRI", 100, regionCode, Duration.ofSeconds(3), Duration.ofSeconds(10), 240);
        RestClient.Builder builder = RestClient.builder()
                .uriBuilderFactory(RestClientConfig.strictUriBuilderFactory(BASE_URL));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder.build(), properties, new JsonMapper());
        return new Fixture(client, server);
    }

    private record Fixture(TourApiClient client, MockRestServiceServer server) {
    }
}
