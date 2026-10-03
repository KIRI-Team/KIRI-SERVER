package com.kiri.server.global.apiPayload.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ExceptionTestController.class, ValidatedTestController.class})
@AutoConfigureMockMvc(addFilters = false)
class GeneralExceptionAdviceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("ProjectException은 에러 코드에 정의된 상태코드와 코드로 응답한다.")
    void projectException() throws Exception {
        mockMvc.perform(get("/test/project-exception"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON403_1"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("@RequestBody @Valid 검증 실패 시 result에 필드별 에러 메시지를 담는다.")
    void methodArgumentNotValid_requestBody() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.name").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.result.global").doesNotExist());
    }

    @Test
    @DisplayName("@ModelAttribute @Valid 검증 실패 시 result에 필드별 에러 메시지를 담는다.")
    void methodArgumentNotValid_modelAttribute() throws Exception {
        mockMvc.perform(get("/test/model-attribute")
                        .param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.name").value("이름은 필수입니다."));
    }

    @Test
    @DisplayName("@RequestBody @Valid 클래스 레벨 제약 실패 시 global 키로 에러 메시지를 담는다.")
    void methodArgumentNotValid_globalError() throws Exception {
        mockMvc.perform(post("/test/global-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.global").value("객체 검증에 실패했습니다."));
    }

    @Test
    @DisplayName("@Validated 컨트롤러의 파라미터 검증 실패 시 result에 파라미터별 에러 메시지를 담는다.")
    void constraintViolation() throws Exception {
        mockMvc.perform(get("/test/validated-param")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.page").value("1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("@Validated 없는 컨트롤러의 파라미터 검증 실패 시 result에 파라미터별 에러 메시지를 담는다.")
    void handlerMethodValidation() throws Exception {
        mockMvc.perform(get("/test/param")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.page").value("1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("메서드 검증에서 단일 파라미터 에러는 파라미터명으로, DTO 필드 에러는 필드명으로 담는다.")
    void handlerMethodValidation_parameterAndDtoField() throws Exception {
        mockMvc.perform(post("/test/method-validation")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\", \"email\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.page").value("1 이상이어야 합니다."))
                .andExpect(jsonPath("$.result.name").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.result.email").value("이메일 형식이 아닙니다."))
                .andExpect(jsonPath("$.result.request").doesNotExist());
    }

    @Test
    @DisplayName("컨트롤러 반환값 검증 실패는 서버 에러이므로 500으로 응답한다.")
    void handlerMethodValidation_returnValue() throws Exception {
        mockMvc.perform(get("/test/return-value"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500_1"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("메서드 검증에서 DTO 클래스 레벨 제약 실패 시 global 키로 에러 메시지를 담는다.")
    void handlerMethodValidation_globalError() throws Exception {
        mockMvc.perform(post("/test/method-validation/global-error")
                        .param("page", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.global").value("객체 검증에 실패했습니다."));
    }

    @Test
    @DisplayName("JSON 형식이 잘못되면 400과 안내 메시지를 반환한다.")
    void httpMessageNotReadable() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result").value("요청 Body 형식이 잘못되었습니다."));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 400으로 응답한다.")
    void missingServletRequestParameter() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("파라미터 타입이 맞지 않으면 400으로 응답한다.")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/param")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type이면 415로 응답한다.")
    void httpMediaTypeNotSupported() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON415_1"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메소드는 405로 응답한다.")
    void httpRequestMethodNotSupported() throws Exception {
        mockMvc.perform(post("/test/unexpected"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON405_1"));
    }

    @Test
    @DisplayName("존재하지 않는 엔드포인트는 404로 응답한다.")
    void noResourceFound() throws Exception {
        mockMvc.perform(get("/test/not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404_1"));
    }

    @Test
    @DisplayName("DB 제약조건 위반 시 409로 응답한다.")
    void dataIntegrityViolation() throws Exception {
        mockMvc.perform(get("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON409_1"));
    }

    @Test
    @DisplayName("따로 처리하지 않은 스프링 MVC 예외는 UNHANDLED_ 코드로 응답한다.")
    void unhandledSpringException() throws Exception {
        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("UNHANDLED_MissingRequestHeaderException"));
    }

    @Test
    @DisplayName("처리되지 않은 예외는 500으로 응답한다.")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500_1"));
    }
}
