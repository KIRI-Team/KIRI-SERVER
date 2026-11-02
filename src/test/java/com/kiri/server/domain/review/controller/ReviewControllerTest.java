package com.kiri.server.domain.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.review.dto.ReviewReqDTO;
import com.kiri.server.domain.review.dto.ReviewResDTO;
import com.kiri.server.domain.review.exception.code.ReviewSuccessCode;
import com.kiri.server.domain.review.servide.ReviewQueryService;
import com.kiri.server.domain.review.servide.ReviewService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ReviewController.class)
@Import(ReviewControllerTest.TestConfig.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private ReviewQueryService reviewQueryService;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @DisplayName("리뷰 작성 성공")
    void createReviewSuccess() throws Exception {
        ReviewReqDTO.Create request = new ReviewReqDTO.Create("가족과 함께 즐거운 시간을 보냈습니다.", 5, true, List.of("review/1/image-1.jpg", "review/1/image-2.jpg"));

        ReviewResDTO.Create response = ReviewResDTO.Create.builder()
                .reviewId(1L)
                .scheduleId(10L)
                .build();

        given(reviewService.createReview(anyLong(), anyLong(), any(ReviewReqDTO.Create.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/reviews", 10L)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
                        ))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(ReviewSuccessCode.REVIEW_CREATED.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ReviewSuccessCode.REVIEW_CREATED.getMessage()))
                .andExpect(jsonPath("$.result.reviewId").value(1))
                .andExpect(jsonPath("$.result.scheduleId").value(10));
    }

    @Test
    @DisplayName("리뷰 내용이 비어있으면 리뷰 작성 실패")
    void createReviewWithEmptyContent() throws Exception {
        ReviewReqDTO.Create request = new ReviewReqDTO.Create("", 5, true, List.of("review/1/image-1.jpg"));

        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/reviews", 10L)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
                        ))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("별점이 1점 미만이면 리뷰 작성 실패")
    void createReviewWithInvalidStarRating() throws Exception {
        ReviewReqDTO.Create request = new ReviewReqDTO.Create("가족과 함께 즐거운 시간을 보냈습니다.", 0, true, List.of("review/1/image-1.jpg"));

        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/reviews", 10L)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
                        ))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사진이 없으면 리뷰 작성 실패")
    void createReviewWithoutImages() throws Exception {
        ReviewReqDTO.Create request = new ReviewReqDTO.Create("가족과 함께 즐거운 시간을 보냈습니다.", 5, true, List.of());

        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/reviews", 10L)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
                        ))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("리뷰 목록 조회 성공")
    void getScheduleReviewsSuccess() throws Exception {
        ReviewResDTO.Item item = ReviewResDTO.Item.builder()
                .reviewId(1L)
                .writer(ReviewResDTO.Writer.builder()
                        .memberId(5L)
                        .nickname("김주연")
                        .familyRole(FamilyRole.DAUGHTER)
                        .build())
                .content("아빠가 자주 가는 식당에 처음 가봤다.")
                .starRating(5)
                .isRejoinIntended(true)
                .imageUrls(List.of("https://cdn.example.com/review/1/image-1.jpg"))
                .createdAt(LocalDateTime.of(2026, 6, 13, 14, 50))
                .isMine(true)
                .build();

        ReviewResDTO.ScheduleReviews response = ReviewResDTO.ScheduleReviews.builder()
                .scheduleId(10L)
                .reviews(List.of(item))
                .build();

        given(reviewQueryService.getScheduleReviews(anyLong(), anyLong())).willReturn(response);

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}/reviews", 10L)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code")
                        .value(ReviewSuccessCode.REVIEW_LIST.getCode()))
                .andExpect(jsonPath("$.result.scheduleId").value(10))
                .andExpect(jsonPath("$.result.reviews[0].reviewId").value(1))
                .andExpect(jsonPath("$.result.reviews[0].writer.nickname").value("김주연"))
                .andExpect(jsonPath("$.result.reviews[0].isMine").value(true));
    }
}
