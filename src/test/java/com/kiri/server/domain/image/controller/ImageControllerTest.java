package com.kiri.server.domain.image.controller;

import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import com.kiri.server.domain.image.exception.code.ImageSuccessCode;
import com.kiri.server.domain.image.service.ImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

    private static final String URL = "/api/v1/images/presigned-url";
    private static final String IMAGE_KEY = "pending/user/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    @Test
    @DisplayName("Presigned URL 발급 성공")
    void issuePresignedUrlSuccess() throws Exception {
        given(imageService.issuePresignedUrl(eq(1L), any(ImageReqDTO.PresignedUrl.class)))
                .willReturn(ImageResDTO.PresignedUrl.builder()
                        .presignedUrl("https://bucket.s3.ap-northeast-2.amazonaws.com/" + IMAGE_KEY + "?X-Amz-Signature=abc")
                        .imageKey(IMAGE_KEY)
                        .expiresAt(LocalDateTime.of(2026, 10, 29, 14, 35))
                        .build());

        mockMvc.perform(post(URL)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/jpeg", "contentLength": 1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value(ImageSuccessCode.PRESIGNED_URL_ISSUED.getCode()))
                .andExpect(jsonPath("$.result.imageKey").value(IMAGE_KEY))
                .andExpect(jsonPath("$.result.presignedUrl").exists())
                .andExpect(jsonPath("$.result.expiresAt").exists());
    }

    @Test
    @DisplayName("contentType이 비어 있으면 발급에 실패한다.")
    @WithMockUser
    void issueWithoutContentType() throws Exception {
        expectBadRequest("""
                {"contentType": " ", "contentLength": 1024}
                """);
    }

    @Test
    @DisplayName("contentLength가 없으면 발급에 실패한다.")
    @WithMockUser
    void issueWithoutContentLength() throws Exception {
        expectBadRequest("""
                {"contentType": "image/jpeg"}
                """);
    }

    @Test
    @DisplayName("contentLength가 0이면 발급에 실패한다.")
    @WithMockUser
    void issueZeroContentLength() throws Exception {
        expectBadRequest("""
                {"contentType": "image/jpeg", "contentLength": 0}
                """);
    }

    @Test
    @DisplayName("지원하지 않는 형식이면 IMAGE400_1로 실패한다.")
    void issueUnsupportedType() throws Exception {
        given(imageService.issuePresignedUrl(eq(1L), any(ImageReqDTO.PresignedUrl.class)))
                .willThrow(new ImageException(ImageErrorCode.UNSUPPORTED_CONTENT_TYPE));

        mockMvc.perform(post(URL)
                        .with(member())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/gif", "contentLength": 1024}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ImageErrorCode.UNSUPPORTED_CONTENT_TYPE.getCode()));
    }

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(post(URL)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(imageService);
    }

    private RequestPostProcessor member() {
        return authentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
