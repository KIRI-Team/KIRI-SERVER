package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleDetailStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ScheduleQueryServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private MemberRepository memberRepository;

    private ScheduleQueryService scheduleQueryService;

    @BeforeEach
    void setUp() {
        scheduleQueryService = new ScheduleQueryService(
                scheduleRepository, scheduleParticipantRepository, memberRepository, CLOCK);
    }

    private ScheduleReqDTO.CalendarCondition condition(int year, int month) {
        return ScheduleReqDTO.CalendarCondition.builder().year(year).month(month).build();
    }

    private Schedule schedule(Long id, LocalDate date, ScheduleStatus status, Family family) {
        return Schedule.builder().id(id).family(family).scheduleDate(date).status(status).build();
    }

    private Schedule upcomingSchedule(Long id, LocalDate date, LocalTime time, ScheduleStatus status,
                                      Family family, String title) {
        BucketItem bucketItem = BucketItem.builder().mission(Mission.builder().title(title).build()).build();
        return Schedule.builder().id(id).family(family).bucketItem(bucketItem)
                .scheduleDate(date).scheduleTime(time).status(status).build();
    }

    private ScheduleParticipant participant(Schedule schedule, ParticipantStatus status, boolean reconfirm) {
        return ScheduleParticipant.builder().schedule(schedule).status(status).reconfirmRequired(reconfirm).build();
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void getCalendar_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getCalendar(MEMBER_ID, condition(2026, 10)))
                .isInstanceOf(MemberException.class);

        verifyNoInteractions(scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("가족공간이 없으면 SCHEDULE403_1로 예외를 던진다.")
    void getCalendar_noFamily() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().build()));

        assertThatThrownBy(() -> scheduleQueryService.getCalendar(MEMBER_ID, condition(2026, 10)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);

        verifyNoInteractions(scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("해당 월에 일정이 없으면 빈 목록을 반환하고 참여자 조회는 하지 않는다.")
    void getCalendar_empty() {
        Family family = Family.builder().build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                any(), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .willReturn(List.of());

        ScheduleResDTO.Calendar result = scheduleQueryService.getCalendar(MEMBER_ID, condition(2026, 10));

        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.month()).isEqualTo(10);
        assertThat(result.schedules()).isEmpty();
        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 전체 상태와 내 응답 상태(초대 안 됨/재확인 필요 포함)를 날짜순으로 반환한다.")
    void getCalendar_derivesMyStatus() {
        Family family = Family.builder().build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));

        Schedule accepted = schedule(100L, LocalDate.of(2026, 10, 3), ScheduleStatus.CONFIRMED, family);
        Schedule notInvited = schedule(101L, LocalDate.of(2026, 10, 10), ScheduleStatus.PENDING_RESPONSE, family);
        Schedule reconfirm = schedule(102L, LocalDate.of(2026, 10, 24), ScheduleStatus.CONFIRMED, family);
        given(scheduleRepository.findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                any(), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .willReturn(List.of(accepted, notInvited, reconfirm));
        given(scheduleParticipantRepository.findBySchedule_IdInAndMember_Id(List.of(100L, 101L, 102L), MEMBER_ID))
                .willReturn(List.of(
                        ScheduleParticipant.builder().schedule(accepted).status(ParticipantStatus.ACCEPTED).reconfirmRequired(false).build(),
                        ScheduleParticipant.builder().schedule(reconfirm).status(ParticipantStatus.ACCEPTED).reconfirmRequired(true).build()
                ));

        ScheduleResDTO.Calendar result = scheduleQueryService.getCalendar(MEMBER_ID, condition(2026, 10));

        assertThat(result.schedules()).extracting(ScheduleResDTO.CalendarItem::scheduleId)
                .containsExactly(100L, 101L, 102L);
        assertThat(result.schedules()).extracting(ScheduleResDTO.CalendarItem::myStatus)
                .containsExactly(CalendarMyStatus.ACCEPTED, CalendarMyStatus.NOT_INVITED, CalendarMyStatus.RECONFIRM_REQUIRED);
        assertThat(result.schedules().get(0).status()).isEqualTo(ScheduleStatus.CONFIRMED);
    }

    @Test
    @DisplayName("다가오는 일정 조회: 회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void getUpcoming_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getUpcoming(MEMBER_ID))
                .isInstanceOf(MemberException.class);

        verifyNoInteractions(scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("다가오는 일정 조회: 가족공간이 없으면 SCHEDULE403_1로 예외를 던진다.")
    void getUpcoming_noFamily() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().build()));

        assertThatThrownBy(() -> scheduleQueryService.getUpcoming(MEMBER_ID))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);

        verifyNoInteractions(scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("응답 필요와 다가오는 확정 일정을 두 슬롯으로 나눠 D-day·타입·활동명과 함께 반환한다.")
    void getUpcoming_bothSlots() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));

        Schedule responseRequired = upcomingSchedule(120L, LocalDate.of(2026, 11, 5), LocalTime.of(18, 0),
                ScheduleStatus.PENDING_RESPONSE, family, "광장시장에서 시장음식 먹어보기");
        Schedule upcoming = upcomingSchedule(118L, LocalDate.of(2026, 11, 3), LocalTime.of(14, 50),
                ScheduleStatus.CONFIRMED, family, "한강 뚜벅뚜벅 축제 가보기");
        given(scheduleRepository.findResponseRequired(any(), eq(MEMBER_ID), any(), any(), any()))
                .willReturn(List.of(responseRequired));
        given(scheduleRepository.findUpcomingConfirmed(any(), eq(MEMBER_ID), any(), any(), any(), any()))
                .willReturn(List.of(upcoming));
        given(scheduleParticipantRepository.findBySchedule_IdInAndMember_Id(any(), eq(MEMBER_ID)))
                .willReturn(List.of(
                        participant(responseRequired, ParticipantStatus.PENDING, false),
                        participant(upcoming, ParticipantStatus.ACCEPTED, false)));

        ScheduleResDTO.UpcomingHome result = scheduleQueryService.getUpcoming(MEMBER_ID);

        assertThat(result.responseRequired().scheduleId()).isEqualTo(120L);
        assertThat(result.responseRequired().type()).isEqualTo(UpcomingCardType.RESPONSE_REQUIRED);
        assertThat(result.responseRequired().myStatus()).isEqualTo(CalendarMyStatus.PENDING);
        assertThat(result.responseRequired().dDay()).isEqualTo(4);
        assertThat(result.responseRequired().title()).isEqualTo("광장시장에서 시장음식 먹어보기");
        assertThat(result.upcoming().scheduleId()).isEqualTo(118L);
        assertThat(result.upcoming().type()).isEqualTo(UpcomingCardType.UPCOMING);
        assertThat(result.upcoming().myStatus()).isEqualTo(CalendarMyStatus.ACCEPTED);
        assertThat(result.upcoming().dDay()).isEqualTo(2);
        assertThat(result.upcoming().title()).isEqualTo("한강 뚜벅뚜벅 축제 가보기");
    }

    @Test
    @DisplayName("당일(D-Day) 확정 일정은 TODAY 타입과 dDay 0으로 반환한다.")
    void getUpcoming_todayType() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));

        Schedule today = upcomingSchedule(200L, LocalDate.of(2026, 11, 1), LocalTime.of(9, 30),
                ScheduleStatus.CONFIRMED, family, "오늘 약속");
        given(scheduleRepository.findResponseRequired(any(), eq(MEMBER_ID), any(), any(), any()))
                .willReturn(List.of());
        given(scheduleRepository.findUpcomingConfirmed(any(), eq(MEMBER_ID), any(), any(), any(), any()))
                .willReturn(List.of(today));
        given(scheduleParticipantRepository.findBySchedule_IdInAndMember_Id(any(), eq(MEMBER_ID)))
                .willReturn(List.of(participant(today, ParticipantStatus.ACCEPTED, false)));

        ScheduleResDTO.UpcomingHome result = scheduleQueryService.getUpcoming(MEMBER_ID);

        assertThat(result.responseRequired()).isNull();
        assertThat(result.upcoming().type()).isEqualTo(UpcomingCardType.TODAY);
        assertThat(result.upcoming().dDay()).isEqualTo(0);
    }

    @Test
    @DisplayName("응답 필요·다가오는 일정이 모두 없으면 두 슬롯 모두 null이고 참여자 조회는 하지 않는다.")
    void getUpcoming_empty() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findResponseRequired(any(), eq(MEMBER_ID), any(), any(), any()))
                .willReturn(List.of());
        given(scheduleRepository.findUpcomingConfirmed(any(), eq(MEMBER_ID), any(), any(), any(), any()))
                .willReturn(List.of());

        ScheduleResDTO.UpcomingHome result = scheduleQueryService.getUpcoming(MEMBER_ID);

        assertThat(result.responseRequired()).isNull();
        assertThat(result.upcoming()).isNull();
        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 상세 조회: 회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void getDetail_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getDetail(MEMBER_ID, 118L))
                .isInstanceOf(MemberException.class);

        verifyNoInteractions(scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 상세 조회: 가족공간이 없으면 SCHEDULE403_1로 예외를 던진다.")
    void getDetail_noFamily() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().build()));

        assertThatThrownBy(() -> scheduleQueryService.getDetail(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);

        verifyNoInteractions(scheduleRepository, scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 상세 조회: 일정이 없으면 SCHEDULE404_2로 예외를 던진다.")
    void getDetail_scheduleNotFound() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findById(118L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getDetail(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 상세 조회: 다른 가족의 일정이면 SCHEDULE404_2로 예외를 던진다.")
    void getDetail_otherFamily() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findById(118L)).willReturn(Optional.of(
                schedule(118L, LocalDate.of(2026, 11, 3), ScheduleStatus.CONFIRMED, Family.builder().id(20L).build())));

        assertThatThrownBy(() -> scheduleQueryService.getDetail(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 상세 조회: 취소된 일정이면 SCHEDULE409_7로 예외를 던진다.")
    void getDetail_cancelled() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findById(118L)).willReturn(Optional.of(
                schedule(118L, LocalDate.of(2026, 11, 3), ScheduleStatus.CANCELLED, family)));

        assertThatThrownBy(() -> scheduleQueryService.getDetail(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_CANCELLED);

        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("일정 상세 조회: 날짜·시간과 참여자별 닉네임·수락 여부를 조회 순서대로 반환하고, 탈퇴한 참여자는 '탈퇴한 사용자'로 표시한다.")
    void getDetail_participants() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        Schedule schedule = Schedule.builder().id(118L).family(family)
                .scheduleDate(LocalDate.of(2026, 11, 3)).scheduleTime(LocalTime.of(14, 50))
                .status(ScheduleStatus.CONFIRMED).build();
        given(scheduleRepository.findById(118L)).willReturn(Optional.of(schedule));
        Member withdrawn = Member.builder().deletedAt(LocalDateTime.of(2026, 10, 30, 12, 0)).build();
        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(118L)).willReturn(List.of(
                ScheduleParticipant.builder().schedule(schedule).member(Member.builder().nickname("엄마").build())
                        .status(ParticipantStatus.ACCEPTED).build(),
                ScheduleParticipant.builder().schedule(schedule).member(Member.builder().nickname("첫째").build())
                        .status(ParticipantStatus.PENDING).build(),
                ScheduleParticipant.builder().schedule(schedule).member(Member.builder().nickname("둘째").build())
                        .status(ParticipantStatus.DECLINED).build(),
                ScheduleParticipant.builder().schedule(schedule).member(withdrawn)
                        .status(ParticipantStatus.ACCEPTED).build(),
                ScheduleParticipant.builder().schedule(schedule).member(null)
                        .status(ParticipantStatus.ACCEPTED).build()));

        ScheduleResDTO.Detail result = scheduleQueryService.getDetail(MEMBER_ID, 118L);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(result.time()).isEqualTo(LocalTime.of(14, 50));
        assertThat(result.participants()).extracting(ScheduleResDTO.ParticipantItem::nickname)
                .containsExactly("엄마", "첫째", "둘째", "탈퇴한 사용자", "탈퇴한 사용자");
        assertThat(result.participants()).extracting(ScheduleResDTO.ParticipantItem::accepted)
                .containsExactly(true, false, false, true, true);
    }

    @ParameterizedTest(name = "{0}, {1} → {2}")
    @CsvSource({
            "PENDING_RESPONSE, 2026-11-03, SCHEDULED",
            "NOT_CONFIRMED, 2026-11-03, SCHEDULED",
            "CONFIRMED, 2026-11-03, CONFIRMED",
            "CONFIRMED, 2026-11-01, TODAY",
            "COMPLETED, 2026-11-01, COMPLETED"
    })
    @DisplayName("일정 상세 조회: 일정 상태와 날짜로 화면 표시용 상태를 계산한다.")
    void getDetail_status(ScheduleStatus status, LocalDate date, ScheduleDetailStatus expected) {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findById(118L)).willReturn(Optional.of(schedule(118L, date, status, family)));
        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(118L)).willReturn(List.of());

        ScheduleResDTO.Detail result = scheduleQueryService.getDetail(MEMBER_ID, 118L);

        assertThat(result.status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("일정 상세 조회: 연결된 미션의 id를 함께 반환한다.")
    void getDetail_missionId() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        Schedule schedule = Schedule.builder().id(118L).family(family)
                .scheduleDate(LocalDate.of(2026, 11, 3)).scheduleTime(LocalTime.of(14, 50))
                .status(ScheduleStatus.CONFIRMED)
                .bucketItem(BucketItem.builder().mission(Mission.builder().id(42L).build()).build())
                .build();
        given(scheduleRepository.findById(118L)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findAllWithMemberByScheduleId(118L)).willReturn(List.of());

        ScheduleResDTO.Detail result = scheduleQueryService.getDetail(MEMBER_ID, 118L);

        assertThat(result.missionId()).isEqualTo(42L);
    }
}
