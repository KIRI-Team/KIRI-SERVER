package com.kiri.server.domain.taste.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.taste.dto.TasteReqDTO;
import com.kiri.server.domain.taste.dto.TasteResDTO;
import com.kiri.server.domain.taste.enums.ActivityLevel;
import com.kiri.server.domain.taste.enums.DurationLevel;
import com.kiri.server.domain.taste.enums.PriceLevel;
import com.kiri.server.domain.taste.exception.TasteException;
import com.kiri.server.domain.taste.exception.code.TasteErrorCode;
import com.kiri.server.domain.taste.exception.code.TasteSuccessCode;
import com.kiri.server.domain.taste.service.TasteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TasteController.class)
@Import(TasteControllerTest.TestConfig.class)
class TasteControllerTest {

    private static final String URL = "/api/v1/members/taste";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TasteService tasteService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @DisplayName("내 취향 조회 성공")
    void getMyTasteSuccess() throws Exception {
        given(tasteService.getMyTaste(1L)).willReturn(response());

        mockMvc.perform(get(URL).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(TasteSuccessCode.GET_SUCCESS.getCode()))
                .andExpect(jsonPath("$.result.nickname").value("쥬123"))
                .andExpect(jsonPath("$.result.isSet").value(true))
                .andExpect(jsonPath("$.result.categories[0]").value("EXHIBITION"))
                .andExpect(jsonPath("$.result.categories[1]").value("FOOD_CAFE"))
                .andExpect(jsonPath("$.result.indoorOutdoor").value("INDOOR"))
                .andExpect(jsonPath("$.result.activityLevel").value("LIGHT"))
                .andExpect(jsonPath("$.result.priceLevel").value("FREE"))
                .andExpect(jsonPath("$.result.durationLevel").value("WITHIN_1H"));
    }

    @Test
    @DisplayName("내 취향 저장 성공")
    void saveMyTasteSuccess() throws Exception {
        TasteReqDTO.Save request = new TasteReqDTO.Save(List.of(Category.EXHIBITION, Category.FOOD_CAFE),
                IndoorOutdoor.INDOOR, ActivityLevel.LIGHT, PriceLevel.FREE, DurationLevel.WITHIN_1H);
        given(tasteService.saveMyTaste(eq(1L), any(TasteReqDTO.Save.class))).willReturn(response());

        mockMvc.perform(put(URL)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(TasteSuccessCode.SAVE_SUCCESS.getCode()))
                .andExpect(jsonPath("$.result.isSet").value(true))
                .andExpect(jsonPath("$.result.categories.length()").value(2));
    }

    @Test
    @DisplayName("상관없음(null) 항목이 있어도 저장할 수 있다.")
    void saveMyTasteWithAny() throws Exception {
        given(tasteService.saveMyTaste(eq(1L), any(TasteReqDTO.Save.class))).willReturn(response());

        mockMvc.perform(put(URL)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories": ["OUTDOOR"], "indoorOutdoor": null, "activityLevel": "ACTIVE",
                                 "priceLevel": null, "durationLevel": null}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("선호 활동이 없으면 저장에 실패한다.")
    @WithMockUser
    void saveWithoutCategory() throws Exception {
        expectBadRequest("""
                {"categories": [], "activityLevel": "LIGHT"}
                """);
    }

    @Test
    @DisplayName("선호 활동이 4개 이상이면 저장에 실패한다.")
    @WithMockUser
    void saveTooManyCategories() throws Exception {
        expectBadRequest("""
                {"categories": ["EXHIBITION", "POPUP", "PERFORMANCE", "FOOD_CAFE"], "activityLevel": "LIGHT"}
                """);
    }

    @Test
    @DisplayName("활동량이 없으면 저장에 실패한다.")
    @WithMockUser
    void saveWithoutActivityLevel() throws Exception {
        expectBadRequest("""
                {"categories": ["EXHIBITION"], "activityLevel": null}
                """);
    }

    @Test
    @DisplayName("없는 값을 보내면 저장에 실패한다.")
    @WithMockUser
    void saveUnknownValue() throws Exception {
        expectBadRequest("""
                {"categories": ["MOVIE"], "activityLevel": "LIGHT"}
                """);
    }

    @Test
    @DisplayName("공간에 BOTH를 보내면 TASTE400_1로 실패한다.")
    void saveBoth() throws Exception {
        given(tasteService.saveMyTaste(eq(1L), any(TasteReqDTO.Save.class)))
                .willThrow(new TasteException(TasteErrorCode.INVALID_INDOOR_OUTDOOR));

        mockMvc.perform(put(URL)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories": ["EXHIBITION"], "indoorOutdoor": "BOTH", "activityLevel": "LIGHT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(TasteErrorCode.INVALID_INDOOR_OUTDOOR.getCode()));
    }

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(put(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tasteService);
    }

    private RequestPostProcessor member() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private TasteResDTO.MyTaste response() {
        return TasteResDTO.MyTaste.builder()
                .nickname("쥬123")
                .isSet(true)
                .categories(List.of(Category.EXHIBITION, Category.FOOD_CAFE))
                .indoorOutdoor(IndoorOutdoor.INDOOR)
                .activityLevel(ActivityLevel.LIGHT)
                .priceLevel(PriceLevel.FREE)
                .durationLevel(DurationLevel.WITHIN_1H)
                .build();
    }
}
