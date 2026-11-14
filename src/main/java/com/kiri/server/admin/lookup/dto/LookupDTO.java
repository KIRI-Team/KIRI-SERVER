package com.kiri.server.admin.lookup.dto;

import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.member.enums.FamilyRole;
import com.kiri.server.domain.member.enums.SocialProvider;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class LookupDTO {

    public enum MemberState { ACTIVE, WITHDRAWN }

    public record MemberCondition(
            @Parameter(description = "회원 ID. 탈퇴 회원은 ID로만 찾을 수 있습니다.") Long memberId,
            @Parameter(description = "닉네임 또는 이메일 부분 일치") String keyword,
            @Parameter(description = "가입 상태") MemberState state,
            @Parameter(description = "가족 연결 여부") Boolean hasFamily,
            @Parameter(description = "가족 역할") FamilyRole familyRole,
            @Parameter(description = "테스트 계정 여부") Boolean isTest,
            Integer page,
            Integer size
    ) {
    }

    public record MemberItem(
            Long memberId,
            String nickname,
            String email,
            SocialProvider socialProvider,
            Long familyId,
            String familyName,
            FamilyRole familyRole,
            boolean isTest,
            boolean withdrawn,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
    }

    public record MemberDetail(
            MemberItem member,
            LocalDateTime familyJoinedAt,
            boolean tasteConfigured,
            List<TermAgreement> terms,
            ActivitySummary activity
    ) {
    }

    public record TermAgreement(String title, Integer termVersion, boolean agreed, LocalDateTime agreedAt) {
    }

    public record ActivitySummary(
            long bucketCount,
            long proposedScheduleCount,
            long participatedScheduleCount,
            long reviewCount,
            long answerCount
    ) {
    }

    public record FamilyCondition(
            @Parameter(description = "가족 ID") Long familyId,
            @Parameter(description = "가족 이름 부분 일치") String keyword,
            @Parameter(description = "테스트 가족 여부") Boolean isTest,
            Integer page,
            Integer size
    ) {
    }

    public record FamilyItem(
            Long familyId,
            String name,
            Long ownerId,
            long memberCount,
            String regionCode,
            boolean isTest,
            LocalDateTime createdAt
    ) {
    }

    public record FamilyDetail(
            FamilyItem family,
            List<FamilyMember> members,
            InviteStatus invite,
            Map<BucketStatus, Long> bucketCounts,
            Map<ScheduleStatus, Long> scheduleCounts,
            long reviewCount
    ) {
    }

    public record FamilyMember(
            Long memberId,
            String nickname,
            FamilyRole familyRole,
            boolean owner,
            boolean isTest,
            boolean withdrawn,
            LocalDateTime familyJoinedAt
    ) {
    }

    public record InviteStatus(boolean active, LocalDateTime expiresAt) {
    }

    public record ScheduleSearchCondition(
            @Parameter(description = "일정 상태") ScheduleStatus status,
            Integer page,
            Integer size
    ) {
    }

    public record ScheduleItem(
            Long scheduleId,
            Long missionId,
            String missionTitle,
            LocalDate scheduleDate,
            LocalTime scheduleTime,
            ScheduleStatus status,
            Long proposerId,
            String proposerNickname,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
    }

    public record ScheduleDetail(
            ScheduleItem schedule,
            Long familyId,
            LocalDateTime responseDeadline,
            LocalDateTime startedAt,
            BucketInfo bucket,
            List<Participant> participants,
            List<ReviewSummary> reviews,
            List<String> warnings
    ) {
    }

    public record BucketInfo(Long bucketItemId, BucketStatus status, LocalDateTime savedAt, LocalDateTime completedAt) {
    }

    public record Participant(
            Long participantId,
            Long memberId,
            String nickname,
            ParticipantStatus status,
            LocalDateTime respondedAt
    ) {
    }

    // 리뷰 본문과 사진은 민감정보 열람 API로만 제공한다.
    public record ReviewSummary(
            Long reviewId,
            Long memberId,
            Integer starRating,
            Boolean isRejoinIntended,
            long photoCount,
            LocalDateTime createdAt
    ) {
    }

    public record TestFlagRequest(@NotNull(message = "isTest는 필수입니다.") Boolean isTest) {
    }

    public record SensitiveRequest(String reason) {
    }

    public record SensitiveFamily(
            Long familyId,
            String postalCode,
            String address,
            String detailAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            List<Answer> recentAnswers,
            List<ReviewContent> reviews
    ) {
    }

    public record Answer(Long memberId, LocalDate questionDate, String question, String content) {
    }

    public record ReviewContent(Long reviewId, Long scheduleId, Long memberId, String content, List<String> photoUrls) {
    }
}
