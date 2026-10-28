package com.kiri.server.domain.bucket.controller;

import com.kiri.server.domain.bucket.dto.BucketReqDTO;
import com.kiri.server.domain.bucket.dto.BucketResDTO;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.exception.BucketException;
import com.kiri.server.domain.bucket.exception.code.BucketErrorCode;
import com.kiri.server.domain.bucket.service.BucketQueryService;
import com.kiri.server.domain.bucket.service.BucketService;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.exception.MissionException;
import com.kiri.server.domain.mission.exception.code.MissionErrorCode;
import com.kiri.server.global.config.SecurityConfig;
import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import com.kiri.server.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @DisplayName("토큰 없이 가족 버킷리스트를 조회하면 COMMON401_1로 응답한다.")
    void getFamilyBuckets_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/bucket-items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("가족 버킷리스트 조회에 성공하면 BUCKET200_1과 저장자 칩, 카드 목록, 다음 커서를 반환한다.")
    void getFamilyBuckets_success() throws Exception {
        given(bucketQueryService.getFamilyBuckets(eq(MEMBER_ID), any())).willReturn(familyBuckets());

        mockMvc.perform(get("/api/v1/bucket-items").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BUCKET200_1"))
                .andExpect(jsonPath("$.result.savers[0].memberId").value(2))
                .andExpect(jsonPath("$.result.savers[0].familyRole").value("FATHER"))
                .andExpect(jsonPath("$.result.savers[0].bucketCount").value(2))
                .andExpect(jsonPath("$.result.bucketItems[0].bucketItemId").value(42))
                .andExpect(jsonPath("$.result.bucketItems[0].status").value("SAVED"))
                .andExpect(jsonPath("$.result.bucketItems[0].mission.title").value("광장시장에서 시장음식 먹어보기"))
                .andExpect(jsonPath("$.result.bucketItems[0].mission.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.bucketItems[0].saver.nickname").value("끼리아빠"))
                .andExpect(jsonPath("$.result.bucketItems[0].saver.withdrawn").value(false))
                .andExpect(jsonPath("$.result.bucketItems[0].schedulable").value(true))
                .andExpect(jsonPath("$.result.bucketItems[1].saver.withdrawn").value(true))
                .andExpect(jsonPath("$.result.bucketItems[1].schedulable").value(false))
                .andExpect(jsonPath("$.result.nextCursor").value(31))
                .andExpect(jsonPath("$.result.hasNext").value(true));
    }

    @Test
    @DisplayName("로그인 회원 ID와 저장자·커서·개수 쿼리 파라미터를 조회 조건으로 받는다.")
    void getFamilyBuckets_bindsCondition() throws Exception {
        given(bucketQueryService.getFamilyBuckets(any(), any())).willReturn(BucketResDTO.FamilyBuckets.builder()
                .savers(List.of()).bucketItems(List.of()).hasNext(false).build());

        mockMvc.perform(get("/api/v1/bucket-items")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("memberId", "2")
                        .param("cursor", "42")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<BucketReqDTO.FamilyBucketCondition> captor =
                ArgumentCaptor.forClass(BucketReqDTO.FamilyBucketCondition.class);
        verify(bucketQueryService).getFamilyBuckets(eq(MEMBER_ID), captor.capture());
        BucketReqDTO.FamilyBucketCondition condition = captor.getValue();
        assertThat(condition.memberId()).isEqualTo(2L);
        assertThat(condition.cursor()).isEqualTo(42L);
        assertThat(condition.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("조회 개수가 1~50을 벗어나면 COMMON400_1과 size 오류 메시지로 응답한다.")
    void getFamilyBuckets_invalidSize() throws Exception {
        mockMvc.perform(get("/api/v1/bucket-items")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.result.size").value("size는 1 이상 50 이하여야 합니다."));
        verifyNoInteractions(bucketQueryService);
    }

    @Test
    @DisplayName("memberId가 숫자가 아니면 COMMON400_1로 응답한다.")
    void getFamilyBuckets_invalidMemberId() throws Exception {
        mockMvc.perform(get("/api/v1/bucket-items")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .param("memberId", "dad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(bucketQueryService);
    }

    @Test
    @DisplayName("가족이 없는 회원이 조회하면 BUCKET403_1로 응답한다.")
    void getFamilyBuckets_familyRequired() throws Exception {
        given(bucketQueryService.getFamilyBuckets(eq(MEMBER_ID), any()))
                .willThrow(new BucketException(BucketErrorCode.FAMILY_REQUIRED));

        mockMvc.perform(get("/api/v1/bucket-items").header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BUCKET403_1"));
    }

    private BucketResDTO.FamilyBuckets familyBuckets() {
        BucketResDTO.FamilyBucketItem saved = BucketResDTO.FamilyBucketItem.builder()
                .bucketItemId(42L)
                .status(BucketStatus.SAVED)
                .savedAt(LocalDateTime.of(2026, 10, 27, 19, 40, 12))
                .mission(BucketResDTO.MissionSummary.builder()
                        .missionId(130L)
                        .title("광장시장에서 시장음식 먹어보기")
                        .status(MissionStatus.ACTIVE)
                        .build())
                .saver(BucketResDTO.Saver.builder()
                        .memberId(2L)
                        .nickname("끼리아빠")
                        .familyRole(FamilyRole.FATHER)
                        .withdrawn(false)
                        .build())
                .schedulable(true)
                .build();
        BucketResDTO.FamilyBucketItem withdrawn = BucketResDTO.FamilyBucketItem.builder()
                .bucketItemId(31L)
                .status(BucketStatus.SAVED)
                .savedAt(LocalDateTime.of(2026, 10, 20, 10, 5))
                .mission(BucketResDTO.MissionSummary.builder()
                        .missionId(98L)
                        .title("한강 뚜벅뚜벅 축제 방문하기")
                        .status(MissionStatus.ENDED)
                        .build())
                .saver(BucketResDTO.Saver.builder().withdrawn(true).build())
                .schedulable(false)
                .build();

        return BucketResDTO.FamilyBuckets.builder()
                .savers(List.of(BucketResDTO.SaverCount.builder()
                        .memberId(2L)
                        .nickname("끼리아빠")
                        .familyRole(FamilyRole.FATHER)
                        .bucketCount(2L)
                        .build()))
                .bucketItems(List.of(saved, withdrawn))
                .nextCursor(31L)
                .hasNext(true)
                .build();
    }
}
