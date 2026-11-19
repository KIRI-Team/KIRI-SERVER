package com.kiri.server.global.security.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CustomAccessDeniedHandlerTest {

    private final CustomAccessDeniedHandler handler=new CustomAccessDeniedHandler(JsonMapper.builder().build());

    @Test
    @DisplayName("권한이 없으면 403과 공통 응답 형식으로 응답한다.")
    void handle() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);

        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"isSuccess\":false")
                .contains("\"code\":\"COMMON403_1\"");
    }
}
