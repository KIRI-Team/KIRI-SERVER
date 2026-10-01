package com.kiri.server.global.config;

import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityTestController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 보호된 경로에 접근하면 401과 공통 응답 형식으로 응답한다.")
    void unauthenticated() throws Exception{
        mockMvc.perform(get("/test/security"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401_1"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("인증된 사용자는 보호된 경로에 접근할 수 있다.")
    void authenticated() throws Exception{
        mockMvc.perform(get("/test/security"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("CSRF가 비활성화되어 있어 토큰 없이 POST 요청이 가능하다.")
    void csrfDisabled() throws Exception{
        mockMvc.perform(post("/test/security"))
                .andExpect(status().isOk());
    }
}
