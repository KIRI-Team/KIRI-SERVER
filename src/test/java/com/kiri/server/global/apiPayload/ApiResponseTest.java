package com.kiri.server.global.apiPayload;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiri.server.global.apiPayload.code.GeneralErrorCode;
import com.kiri.server.global.apiPayload.code.GeneralSuccessCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ApiResponseTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    @DisplayName("onSuccess(code, result)는 성공 코드와 result를 담는다.")
    void onSuccess_withResult() {
        ApiResponse<String> response = ApiResponse.onSuccess(GeneralSuccessCode.OK, "result");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GeneralSuccessCode.OK.getCode());
        assertThat(response.getMessage()).isEqualTo(GeneralSuccessCode.OK.getMessage());
        assertThat(response.getResult()).isEqualTo("result");
    }

    @Test
    @DisplayName("onSuccess(code)는 result를 null로 반환한다.")
    void onSuccess_withNullResult() {
        ApiResponse<Void> response = ApiResponse.onSuccess(GeneralSuccessCode.OK);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(GeneralSuccessCode.OK.getCode());
        assertThat(response.getMessage()).isEqualTo(GeneralSuccessCode.OK.getMessage());
        assertThat(response.getResult()).isNull();
    }

    @Test
    @DisplayName("onFailure는 isSuccess=false와 에러 코드를 담는다.")
    void onFailure() {
        ApiResponse<String> response = ApiResponse.onFailure(GeneralErrorCode.INTERNAL_SERVER_ERROR, "result");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.getResult()).isEqualTo("result");
    }

    @Test
    @DisplayName("onFailure(code)는 result를 null로 반환한다.")
    void onFailure_withNullResult() {
        ApiResponse<Void> response = ApiResponse.onFailure(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo(GeneralErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.getResult()).isNull();
    }

    @Test
    @DisplayName("JSON 직렬화 시 키 순서가 고정되고 success 키는 중복으로 나오지 않는다.")
    void serialize_keyOrderAndNoDuplicateSuccess() {
        String json = jsonMapper.writeValueAsString(ApiResponse.onSuccess(GeneralSuccessCode.OK));

        assertThat(json)
                .isEqualTo(
                        "{\"isSuccess\":true,\"code\":\"COMMON200_1\",\"message\":\"성공적으로 요청을 처리했습니다.\",\"result\":null}");
    }
}
