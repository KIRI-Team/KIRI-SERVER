package com.kiri.server.domain.family.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.family.dto.FamilyReqDTO;
import com.kiri.server.domain.family.dto.FamilyResDTO;
import com.kiri.server.domain.family.exception.FamilyException;
import com.kiri.server.domain.family.exception.code.FamilyErrorCode;
import com.kiri.server.domain.family.exception.code.FamilySuccessCode;
import com.kiri.server.domain.family.service.FamilyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(FamilyController.class)
@Import(FamilyControllerTest.TestConfig.class)
class FamilyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FamilyService familyService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final Long MEMBER_ID = 1L;
    private static final Long FAMILY_ID = 10L;

    private static Stream<Arguments> invalidAddressRequests() {
        return Stream.of(
                Arguments.of(
                        "우편번호 누락",
                        """
                        {
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "latitude": 37.5563,
                            "longitude": 126.9220
                        }
                        """
                ),
                Arguments.of(
                        "우편번호 형식 오류",
                        """
                        {
                            "postalCode": "1234",
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "latitude": 37.5563,
                            "longitude": 126.9220
                        }
                        """
                ),
                Arguments.of(
                        "기본 주소 누락",
                        """
                        {
                            "postalCode": "12345",
                            "address": "",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "latitude": 37.5563,
                            "longitude": 126.9220
                        }
                        """
                ),
                Arguments.of(
                        "법정동 코드 누락",
                        """
                        {
                            "postalCode": "12345",
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "",
                            "latitude": 37.5563,
                            "longitude": 126.9220
                        }
                        """
                ),
                Arguments.of(
                        "위도 누락",
                        """
                        {
                            "postalCode": "12345",
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "longitude": 126.9220
                        }
                        """
                ),
                Arguments.of(
                        "위도 범위 초과",
                        """
                        {
                            "postalCode": "12345",
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "latitude": 91.0,
                            "longitude": 126.9220
                        }
                        """
                ),
                Arguments.of(
                        "경도 누락",
                        """
                        {
                            "postalCode": "12345",
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "latitude": 37.5563
                        }
                        """
                ),
                Arguments.of(
                        "경도 범위 초과",
                        """
                        {
                            "postalCode": "12345",
                            "address": "서울특별시 마포구 양화로 123",
                            "detailAddress": "101동 202호",
                            "regionCode": "1144012300",
                            "latitude": 37.5563,
                            "longitude": 181.0
                        }
                        """
                )
        );
    }

    @Test
    @DisplayName("가족 공간 생성 성공")
    void createFamilySuccess() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("끼리네 가족");

        FamilyResDTO.Create response = FamilyResDTO.Create.builder()
                .familyId(1L)
                .name("끼리네 가족")
                .inviteCode("ABC123")
                .inviteUrl("https://kiri.app/invite/test-token")
                .inviteExpiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(familyService.createFamily(anyLong(), any(FamilyReqDTO.Create.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/families")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        1L,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                )
                        ))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(FamilySuccessCode.FAMILY_CREATED.getCode()))
                .andExpect(jsonPath("$.message").value(FamilySuccessCode.FAMILY_CREATED.getMessage()))
                .andExpect(jsonPath("$.result.familyId").value(1))
                .andExpect(jsonPath("$.result.name").value("끼리네 가족"))
                .andExpect(jsonPath("$.result.inviteCode").value("ABC123"))
                .andExpect(jsonPath("$.result.inviteUrl")
                        .value("https://kiri.app/invite/test-token"))
                .andExpect(jsonPath("$.result.inviteExpiresAt").exists());
    }

    @Test
    @DisplayName("가족 이름이 비어있으면 가족 공간 생성 실패")
    @WithMockUser
    void createFamilyEmptyName() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("");

        mockMvc.perform(post("/api/v1/families")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가족 이름이 1자이면 가족 공간 생성 실패")
    @WithMockUser
    void createFamilyNameTooShort() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("가");

        mockMvc.perform(post("/api/v1/families")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가족 이름이 20자를 초과하면 가족 공간 생성 실패")
    @WithMockUser
    void createFamilyNameTooLong() throws Exception{
        FamilyReqDTO.Create request = new FamilyReqDTO.Create("가나다라마바사아자차카타파하가나다라마바사아");

        mockMvc.perform(post("/api/v1/families")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("잘못된 주소 정보로 요청하면 400 Bad Request를 반환한다.")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAddressRequests")
    void updateFamilyAddressInvalidRequest(String description, String requestBody) throws Exception {

        var authentication = new UsernamePasswordAuthenticationToken(MEMBER_ID, null, List.of());

        mockMvc.perform(put("/api/v1/families/{familyId}/address", FAMILY_ID)
                        .with(authentication(authentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(familyService);
    }

    @Test
    @DisplayName("가족 ID 조회 성공")
    void getFamilyIdSuccess() throws Exception {
        Long memberId = 1L;
        Long familyId = 10L;

        when(familyService.getFamilyId(memberId)).thenReturn(new FamilyResDTO.Id(familyId));

        var authentication = new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(get("/api/v1/families/me/id")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.familyId").value(familyId));

        verify(familyService).getFamilyId(memberId);
    }

    @Test
    @DisplayName("인증되지 않은 사용자가 가족 ID 조회 시 401 예외 발생")
    void getFamilyIdFailUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/families/me/id"))
                .andExpect(status().isUnauthorized());

        verify(familyService, never()).getFamilyId(any());
    }

    @Test
    @DisplayName("가족에 소속되지 않은 사용자가 가족 ID 조회 시 예외 발생")
    void getFamilyIdFailNotJoinedFamily() throws Exception {
        Long memberId = 1L;

        var authentication = new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(familyService.getFamilyId(memberId)).thenThrow(new FamilyException(FamilyErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/families/me/id")
                        .with(authentication(authentication)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.message").value("존재하지 않는 가족입니다."));

        verify(familyService).getFamilyId(memberId);
    }

    @Test
    @DisplayName("유효한 초대 코드 입력 시 가족 가입에 성공한다.")
    void joinFamilySuccessfully() throws Exception {
        Long memberId = 1L;
        String inviteCode = "ABC123";

        FamilyReqDTO.Join request = new FamilyReqDTO.Join(inviteCode);
        FamilyResDTO.Join response = new FamilyResDTO.Join(10L, "우리 가족");

        given(familyService.joinFamily(eq(memberId), any(FamilyReqDTO.Join.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/families/join/code")
                        .with(authentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.familyId").value(10L))
                .andExpect(jsonPath("$.result.familyName").value("우리 가족"));

        then(familyService).should().joinFamily(eq(memberId), any(FamilyReqDTO.Join.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC12", "abc123", "ABC12!", "ABCDEFG"})
    @DisplayName("잘못된 형식의 초대 코드 입력 시 400 에러가 발생한다.")
    void joinFamilyWithInvalidInviteCodeFormat(String inviteCode) throws Exception {
        FamilyReqDTO.Join request = new FamilyReqDTO.Join(inviteCode);

        mockMvc.perform(post("/api/v1/families/join/code")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        then(familyService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("빈 초대 코드 입력 시 400 에러가 발생한다.")
    void joinFamilyWithBlankInviteCode() throws Exception {
        FamilyReqDTO.Join request = new FamilyReqDTO.Join("");

        mockMvc.perform(post("/api/v1/families/join/code")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        then(familyService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유효한 초대 토큰으로 가족 정보를 조회한다.")
    void getInviteTokenInfoSuccessfully() throws Exception {
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        FamilyResDTO.InviteTokenInfo response = new FamilyResDTO.InviteTokenInfo(10L, "우리 가족");

        given(familyService.getInviteTokenInfo(inviteToken)).willReturn(response);

        mockMvc.perform(get("/api/v1/families/invite/{inviteToken}", inviteToken)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.familyId").value(10L))
                .andExpect(jsonPath("$.result.familyName").value("우리 가족"));

        then(familyService).should()
                .getInviteTokenInfo(inviteToken);
    }

    @Test
    @DisplayName("유효한 초대 토큰으로 가족 가입에 성공한다.")
    void joinFamilyByTokenSuccessfully() throws Exception {
        Long memberId = 1L;
        String inviteToken = "a1b2c3d4e5f678901234567890abcdef";

        FamilyResDTO.Join response = new FamilyResDTO.Join(10L, "우리 가족");

        given(familyService.joinFamilyByToken(memberId, inviteToken)).willReturn(response);

        mockMvc.perform(post("/api/v1/families/join/{inviteToken}", inviteToken)
                        .with(authentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.familyId").value(10L))
                .andExpect(jsonPath("$.result.familyName").value("우리 가족"));

        then(familyService).should().joinFamilyByToken(memberId, inviteToken);
    }
}