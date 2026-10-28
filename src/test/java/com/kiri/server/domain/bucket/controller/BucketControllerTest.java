package com.kiri.server.domain.bucket.controller;

import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.exception.BucketException;
import com.kiri.server.domain.bucket.exception.code.BucketErrorCode;
import com.kiri.server.domain.bucket.service.BucketQueryService;
import com.kiri.server.domain.bucket.service.BucketService;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BucketController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class, JwtProvider.class})
@TestPropertySource(properties = "jwt.secret=" + BucketControllerTest.SECRET)
class BucketControllerTest {

    static final String SECRET = "test-secret-key-for-jwt-must-be-at-least-32-bytes";
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private BucketService bucketService;

    @MockitoBean
    private BucketQueryService bucketQueryService;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        bearerToken = "Bearer " + jwtProvider.createAccessToken(MEMBER_ID);
    }

    @Test
    @DisplayName("토큰 없이 저장하면 COMMON401_1로 응답한다.")
    void saveBucket_noToken() throws Exception {
        mockMvc.perform(post("/api/v1/missions/130/bucket"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("저장에 성공하면 BUCKET201_1과 저장된 항목 정보를 반환한다.")
    void saveBucket_success() throws Exception {
        given(bucketService.saveBucket(MEMBER_ID, 130L)).willReturn(BucketResDTO.Saved.builder()
                .bucketItemId(15L)
                .missionId(130L)
                .status(BucketStatus.SAVED)
                .savedAt(LocalDateTime.of(2026, 10, 27, 19, 40, 12))
                .build());

        mockMvc.perform(post("/api/v1/missions/130/bucket").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BUCKET201_1"))
                .andExpect(jsonPath("$.result.bucketItemId").value(15))
                .andExpect(jsonPath("$.result.missionId").value(130))
                .andExpect(jsonPath("$.result.status").value("SAVED"));
    }

    @Test
    @DisplayName("이미 저장한 미션이면 BUCKET409_1로 응답한다.")
    void saveBucket_alreadySaved() throws Exception {
        given(bucketService.saveBucket(MEMBER_ID, 130L)).willThrow(new BucketException(BucketErrorCode.ALREADY_SAVED));

        mockMvc.perform(post("/api/v1/missions/130/bucket").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUCKET409_1"));
    }

    @Test
    @DisplayName("저장할 수 없는 미션이면 BUCKET409_2로 응답한다.")
    void saveBucket_notSavable() throws Exception {
        given(bucketService.saveBucket(MEMBER_ID, 130L)).willThrow(new BucketException(BucketErrorCode.MISSION_NOT_SAVABLE));

        mockMvc.perform(post("/api/v1/missions/130/bucket").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUCKET409_2"));
    }

    @Test
    @DisplayName("가족이 없는 회원이면 BUCKET403_1로 응답한다.")
    void saveBucket_familyRequired() throws Exception {
        given(bucketService.saveBucket(MEMBER_ID, 130L)).willThrow(new BucketException(BucketErrorCode.FAMILY_REQUIRED));

        mockMvc.perform(post("/api/v1/missions/130/bucket").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BUCKET403_1"));
    }

    @Test
    @DisplayName("노출할 수 없는 미션이면 MISSION404_1로 응답한다.")
    void saveBucket_missionNotFound() throws Exception {
        given(bucketService.saveBucket(MEMBER_ID, 99L)).willThrow(new MissionException(MissionErrorCode.NOT_FOUND));

        mockMvc.perform(post("/api/v1/missions/99/bucket").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MISSION404_1"));
    }

    @Test
    @DisplayName("missionId가 숫자가 아니면 COMMON400_1로 응답한다.")
    void saveBucket_invalidId() throws Exception {
        mockMvc.perform(post("/api/v1/missions/abc/bucket").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(bucketService);
    }
}
