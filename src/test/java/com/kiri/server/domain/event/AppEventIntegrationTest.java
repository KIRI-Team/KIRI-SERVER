package com.kiri.server.domain.event;

import com.kiri.server.admin.AdminFixtures;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.global.security.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AdminFixtures.class)
class AppEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminFixtures fx;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private EntityManager em;

    private String token;
    private Family family;

    @BeforeEach
    void setUp() {
        Member member = fx.member("이벤트");
        family = fx.family("이벤트가족", member);
        token = "Bearer " + jwtProvider.createAccessToken(member.getId());
    }

    @Test
    @DisplayName("이벤트를 저장하고 회원의 가족을 함께 기록하며, 같은 eventId 재전송은 무시한다.")
    void record_andDeduplicate() throws Exception {
        String body = "{\"events\":[" + event("e-1", "kiri_pick_click") + "," + event("e-2", "mission_card_impression") + "]}";
        mockMvc.perform(post("/api/v1/events").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.inserted").value(2));
        mockMvc.perform(post("/api/v1/events").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.result.inserted").value(0));

        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT family_id, properties ->> 'kiriPickId' FROM app_event WHERE event_id = 'e-1'")
                .getSingleResult();
        assertThat(((Number) row[0]).longValue()).isEqualTo(family.getId());
        assertThat(row[1]).isEqualTo("7");
    }

    @Test
    @DisplayName("한 번에 100건을 넘기면 400이다.")
    void record_tooMany() throws Exception {
        StringJoiner events = new StringJoiner(",");
        for (int i = 0; i < 101; i++) {
            events.add(event("bulk-" + i, "mission_card_impression"));
        }
        mockMvc.perform(post("/api/v1/events").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + events + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰이 없으면 401이다.")
    void record_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + event("e-x", "x") + "]}"))
                .andExpect(status().isUnauthorized());
    }

    private static String event(String id, String name) {
        return """
                {"eventId":"%s","eventName":"%s","properties":{"kiriPickId":7,"position":1},
                 "platform":"ANDROID","appVersion":"1.2.0","occurredAt":"2026-11-10T10:00:00"}
                """.formatted(id, name);
    }
}
