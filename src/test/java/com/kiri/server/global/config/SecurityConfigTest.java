package com.kiri.server.global.config;

import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityTestController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + SecurityConfigTest.SECRET)
class SecurityConfigTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("토큰 없이 보호된 경로에 접근하면 COMMON401_1로 응답한다.")
    void noToken() throws Exception {
        mockMvc.perform(get("/test/security"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401_1"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("유효한 토큰이면 보호된 경로에 접근할 수 있다.")
    void validToken() throws Exception {
        String token = jwtProvider.createAccessToken(1L);

        mockMvc.perform(get("/test/security")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("만료된 토큰이면 AUTH401_2로 응답한다.")
    void expiredToken() throws Exception {
        String token = Jwts.builder()
                .subject("1")
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/test/security")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_2"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 AUTH401_1로 응답한다.")
    void invalidToken() throws Exception {
        mockMvc.perform(get("/test/security")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 토큰 없음으로 처리한다.")
    void notBearer() throws Exception {
        String token = jwtProvider.createAccessToken(1L);

        mockMvc.perform(get("/test/security")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @WithMockUser
    @DisplayName("CSRF가 비활성화되어 있어 토큰 없이 POST 요청이 가능하다.")
    void csrfDisabled() throws Exception {
        mockMvc.perform(post("/test/security"))
                .andExpect(status().isOk());
    }
}
