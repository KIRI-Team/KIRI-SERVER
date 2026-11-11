package com.kiri.server.admin.auth;

import com.kiri.server.admin.AdminTestSupport;
import com.kiri.server.admin.audit.enums.AuditAction;
import com.kiri.server.admin.audit.enums.AuditTargetType;
import com.kiri.server.admin.audit.repository.AdminAuditLogRepository;
import com.kiri.server.admin.auth.entity.Admin;
import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.global.security.jwt.AdminJwtProvider;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AdminTestSupport.class)
class AdminAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestSupport support;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private AdminJwtProvider adminJwtProvider;
    @Autowired
    private EntityManager entityManager;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = support.createAdmin("ops-admin");
    }

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 토큰을 발급하고 LOGIN 이력을 남긴다.")
    void login_success() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ops-admin\",\"password\":\"" + AdminTestSupport.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ADMIN200_1"))
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty());

        assertThat(adminAuditLogRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType.ADMIN, admin.getId()))
                .extracting("action").containsExactly(AuditAction.LOGIN);
    }

    @Test
    @DisplayName("아이디가 없을 때와 비밀번호가 틀렸을 때 같은 에러 코드를 돌려준다.")
    void login_failure_sameError() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ops-admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401_1"));
        mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"nobody\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401_1"));
    }

    @Test
    @DisplayName("리프레시 토큰으로 재발급하면 이전 리프레시 토큰은 더 쓸 수 없다.")
    void reissue_rotates() throws Exception {
        String body = mockMvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ops-admin\",\"password\":\"" + AdminTestSupport.PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String refresh = body.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");
        Thread.sleep(1100); // iat가 초 단위라 같은 초에 만들면 같은 토큰이 된다.

        mockMvc.perform(post("/api/admin/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN401_2"));
    }

    @Test
    @DisplayName("토큰 없이 관리자 API를 호출하면 401이다.")
    void noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    @DisplayName("앱 사용자 토큰으로는 관리자 API에 접근할 수 없다.")
    void appToken_cannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(admin.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("관리자 토큰으로는 앱 API에 접근할 수 없다.")
    void adminToken_cannotAccessApp() throws Exception {
        mockMvc.perform(get("/api/v1/missions").header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    @DisplayName("관리자 리프레시 토큰을 액세스 토큰처럼 쓰면 거부한다.")
    void refreshToken_asAccess_rejected() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminJwtProvider.createRefreshToken(admin.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비활성화된 관리자의 토큰은 즉시 거부한다.")
    void disabledAdmin_tokenRejected() throws Exception {
        Admin other = support.createAdmin("other-admin");
        String otherToken = support.bearer(other);
        mockMvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.loginId").value("other-admin"));

        mockMvc.perform(patch("/api/admin/admins/" + other.getId())
                        .header(HttpHeaders.AUTHORIZATION, support.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.enabled").value(false));
        entityManager.flush();

        mockMvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, otherToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자를 만들면 CREATE 이력이 남고, 같은 아이디는 409이다.")
    void createAdmin_auditAndDuplicate() throws Exception {
        String body = "{\"loginId\":\"new-admin\",\"password\":\"password-123\",\"name\":\"신규\"}";
        mockMvc.perform(post("/api/admin/admins").header(HttpHeaders.AUTHORIZATION, support.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.loginId").value("new-admin"));
        Long createdId = adminRepository.findByLoginId("new-admin").orElseThrow().getId();
        assertThat(adminAuditLogRepository.findAllByTargetTypeAndTargetIdOrderByIdDesc(AuditTargetType.ADMIN, createdId))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
                    assertThat(log.getAdminId()).isEqualTo(admin.getId());
                    assertThat(log.getAfterData()).contains("new-admin");
                });

        mockMvc.perform(post("/api/admin/admins").header(HttpHeaders.AUTHORIZATION, support.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN409_1"));
    }

    @Test
    @DisplayName("자기 자신은 비활성화할 수 없다.")
    void cannotDisableSelf() throws Exception {
        mockMvc.perform(patch("/api/admin/admins/" + admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, support.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("관리자 OpenAPI 문서에는 관리자 경로만, 앱 문서에는 앱 경로만 들어간다.")
    void openApiGroups() throws Exception {
        String admin = mockMvc.perform(get("/v3/api-docs/admin")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String app = mockMvc.perform(get("/v3/api-docs/app")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(admin).contains("\"/api/admin/auth/login\"").doesNotContain("\"/api/v1/");
        assertThat(app).contains("\"/api/v1/missions\"").doesNotContain("\"/api/admin/");
    }

    @Test
    @DisplayName("감사 로그를 대상 종류로 검색한다.")
    void searchAuditLogs() throws Exception {
        mockMvc.perform(post("/api/admin/admins").header(HttpHeaders.AUTHORIZATION, support.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"audit-target\",\"password\":\"password-123\",\"name\":\"대상\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs").param("targetType", "ADMIN").param("adminId", admin.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, support.bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.result.content[0].adminName").value("ops-admin-name"));
    }
}
