package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.mission.enums.WalkingLevel;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleDetailStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.enums.UpcomingCardType;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleConditionRepository;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import com.kiri.server.domain.taste.dto.TasteProfileDTO;
import com.kiri.server.domain.taste.service.TasteProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyDouble;
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
    private ScheduleConditionRepository scheduleConditionRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private TasteProfileService tasteProfileService;

    private ScheduleQueryService scheduleQueryService;

    @BeforeEach
    void setUp() {
        scheduleQueryService = new ScheduleQueryService(
                scheduleRepository, scheduleParticipantRepository, scheduleConditionRepository, memberRepository,
                missionRepository, tasteProfileService, CLOCK);
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

    private Schedule conditionSchedule(Long id, Family family) {
        Mission mission = Mission.builder()
                .title("한강 뚜벅뚜벅 축제 가보기")
                .walkingLevel(WalkingLevel.MEDIUM)
                .durationMinMinutes(60)
                .durationMaxMinutes(180)
                .build();
        BucketItem bucketItem = BucketItem.builder().mission(mission).build();
        return Schedule.builder().id(id).family(family).bucketItem(bucketItem).status(ScheduleStatus.CONFIRMED)
                .scheduleDate(LocalDate.of(2026, 11, 1)).build();
    }

    private void stubFinalParticipant(Long scheduleId, boolean finalParticipant) {
        given(scheduleParticipantRepository.existsBySchedule_IdAndMember_IdAndStatusAndReconfirmRequiredFalse(
                scheduleId, MEMBER_ID, ParticipantStatus.ACCEPTED)).willReturn(finalParticipant);
    }

    private ScheduleCondition condition(Schedule schedule, ConditionType type) {
        return ScheduleCondition.builder().schedule(schedule).conditionType(type).build();
    }

    @Test
    @DisplayName("컨디션 조회: 회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void getConditions_memberNotFound() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getConditions(MEMBER_ID, 118L))
                .isInstanceOf(MemberException.class);

        verifyNoInteractions(scheduleRepository, scheduleConditionRepository);
    }

    @Test
    @DisplayName("컨디션 조회: 가족공간이 없으면 SCHEDULE403_1로 예외를 던진다.")
    void getConditions_noFamily() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().build()));

        assertThatThrownBy(() -> scheduleQueryService.getConditions(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);

        verifyNoInteractions(scheduleRepository, scheduleConditionRepository);
    }

    @Test
    @DisplayName("컨디션 조회: 없거나 다른 가족의 일정이면 SCHEDULE404_2로 예외를 던지고 조건은 조회하지 않는다.")
    void getConditions_scheduleNotFound() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findWithMissionByIdAndFamilyId(118L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getConditions(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("컨디션 조회: 최종 참여자가 아니면 SCHEDULE403_5로 예외를 던지고 조건은 조회하지 않는다.")
    void getConditions_notFinalParticipant() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findWithMissionByIdAndFamilyId(118L, 10L))
                .willReturn(Optional.of(conditionSchedule(118L, family)));
        stubFinalParticipant(118L, false);

        assertThatThrownBy(() -> scheduleQueryService.getConditions(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_FINAL_PARTICIPANT);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("컨디션 조회: 확정되지 않은 일정이면 SCHEDULE409_6으로 예외를 던지고 조건은 조회하지 않는다.")
    void getConditions_notConfirmed() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        Schedule schedule = Schedule.builder().id(118L).family(family).status(ScheduleStatus.PENDING_RESPONSE).build();
        given(scheduleRepository.findWithMissionByIdAndFamilyId(118L, 10L)).willReturn(Optional.of(schedule));
        stubFinalParticipant(118L, true);

        assertThatThrownBy(() -> scheduleQueryService.getConditions(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.CONDITION_NOT_VIEWABLE);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("컨디션 조회: 일정 당일이 아니면 SCHEDULE409_6으로 예외를 던지고 조건은 조회하지 않는다.")
    void getConditions_notToday() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        Schedule schedule = Schedule.builder().id(118L).family(family).status(ScheduleStatus.CONFIRMED)
                .scheduleDate(LocalDate.of(2026, 11, 2)).build();
        given(scheduleRepository.findWithMissionByIdAndFamilyId(118L, 10L)).willReturn(Optional.of(schedule));
        stubFinalParticipant(118L, true);

        assertThatThrownBy(() -> scheduleQueryService.getConditions(MEMBER_ID, 118L))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.CONDITION_NOT_VIEWABLE);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("컨디션 조회: 미션 정보와 적용된 조건을 enum 순서로 정렬해 반환한다.")
    void getConditions_success() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        Schedule schedule = conditionSchedule(118L, family);
        given(scheduleRepository.findWithMissionByIdAndFamilyId(118L, 10L)).willReturn(Optional.of(schedule));
        stubFinalParticipant(118L, true);
        given(scheduleConditionRepository.findBySchedule_Id(118L)).willReturn(List.of(
                condition(schedule, ConditionType.LOWER_BUDGET),
                condition(schedule, ConditionType.REDUCE_STEPS)));

        ScheduleResDTO.Conditions result = scheduleQueryService.getConditions(MEMBER_ID, 118L);

        assertThat(result.missionTitle()).isEqualTo("한강 뚜벅뚜벅 축제 가보기");
        assertThat(result.walkingLevel()).isEqualTo(WalkingLevel.MEDIUM);
        assertThat(result.durationMinMinutes()).isEqualTo(60);
        assertThat(result.durationMaxMinutes()).isEqualTo(180);
        assertThat(result.conditions()).containsExactly(ConditionType.REDUCE_STEPS, ConditionType.LOWER_BUDGET);
    }

    @Test
    @DisplayName("컨디션 조회: 적용한 조건이 없으면 빈 목록을 반환한다.")
    void getConditions_noConditions() {
        Family family = Family.builder().id(10L).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findWithMissionByIdAndFamilyId(118L, 10L))
                .willReturn(Optional.of(conditionSchedule(118L, family)));
        stubFinalParticipant(118L, true);
        given(scheduleConditionRepository.findBySchedule_Id(118L)).willReturn(List.of());

        ScheduleResDTO.Conditions result = scheduleQueryService.getConditions(MEMBER_ID, 118L);

        assertThat(result.missionTitle()).isEqualTo("한강 뚜벅뚜벅 축제 가보기");
        assertThat(result.conditions()).isEmpty();
    }

    private static final Long SCHEDULE_ID = 118L;
    private static final Long FAMILY_ID = 10L;
    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2026, 11, 15);
    private static final double BASE_LATITUDE = 37.5100;
    private static final double BASE_LONGITUDE = 126.9960;
    // 위도 0.009도 ≈ 1km
    private static final double LATITUDE_PER_KM = 0.009;

    private final Place basePlace = place(1L, "여의도 한강공원", 0, null);
    private long nextMissionId = 100L;

    private Place place(Long id, String name, double kmNorth, String imageUrl) {
        return Place.builder()
                .id(id)
                .name(name)
                .address("서울특별시 서초구 올림픽대로 2085-14")
                .latitude(BigDecimal.valueOf(BASE_LATITUDE + kmNorth * LATITUDE_PER_KM))
                .longitude(BigDecimal.valueOf(BASE_LONGITUDE))
                .imageUrl(imageUrl)
                .build();
    }

    private Mission mission(Place place, Category category, IndoorOutdoor indoorOutdoor, Integer price) {
        return Mission.builder()
                .id(nextMissionId++)
                .place(place)
                .category(category)
                .indoorOutdoor(indoorOutdoor)
                .price(price)
                .recommendationReason(place.getName() + " " + category)
                .imageUrl("https://mission/" + place.getName())
                .build();
    }

    private ScheduleParticipant nearbyParticipant(Long memberId, ParticipantStatus status) {
        return ScheduleParticipant.builder().member(Member.builder().id(memberId).build()).status(status).build();
    }

    private TasteProfileDTO.MemberProfile taste(Long memberId, Category... categories) {
        return TasteProfileDTO.MemberProfile.builder().memberId(memberId).isSet(true).categories(List.of(categories))
                .build();
    }

    private void stubSchedule() {
        Family family = Family.builder().id(FAMILY_ID).build();
        BucketItem bucketItem = BucketItem.builder().mission(Mission.builder().place(basePlace).build()).build();
        Schedule schedule = Schedule.builder().id(SCHEDULE_ID).family(family).bucketItem(bucketItem)
                .scheduleDate(SCHEDULE_DATE).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findWithMissionByIdAndFamilyId(SCHEDULE_ID, FAMILY_ID)).willReturn(Optional.of(schedule));
    }

    private void stubNearby(List<Mission> missions, List<ScheduleParticipant> participants,
                            List<TasteProfileDTO.MemberProfile> profiles, List<ConditionType> conditions) {
        stubSchedule();
        List<Long> ids = missions.stream().map(Mission::getId).toList();
        given(missionRepository.findNearbyIds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), eq(basePlace.getId()), eq(SCHEDULE_DATE))).willReturn(ids);
        given(missionRepository.findAllWithPlaceByIdIn(ids)).willReturn(missions);
        given(scheduleParticipantRepository.findBySchedule_Id(SCHEDULE_ID)).willReturn(participants);
        given(tasteProfileService.getFamilyProfile(FAMILY_ID))
                .willReturn(TasteProfileDTO.FamilyProfile.builder().familyId(FAMILY_ID).members(profiles).build());
        Schedule schedule = Schedule.builder().id(SCHEDULE_ID).build();
        given(scheduleConditionRepository.findBySchedule_Id(SCHEDULE_ID)).willReturn(conditions.stream()
                .map(type -> ScheduleCondition.builder().schedule(schedule).conditionType(type).build())
                .toList());
    }

    private List<String> placeNames(List<ScheduleResDTO.NearbyItem> items) {
        return items.stream().map(ScheduleResDTO.NearbyItem::placeName).toList();
    }

    @Test
    @DisplayName("가족공간이 없으면 근처 놀거리를 조회하지 않고 SCHEDULE403_1로 예외를 던진다.")
    void getNearby_noFamily() {
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().build()));

        assertThatThrownBy(() -> scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);

        verifyNoInteractions(scheduleRepository, missionRepository);
    }

    @Test
    @DisplayName("없거나 다른 가족의 일정이면 근처 놀거리를 조회하지 않고 SCHEDULE404_2로 예외를 던진다.")
    void getNearby_scheduleNotFound() {
        Family family = Family.builder().id(FAMILY_ID).build();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(Member.builder().family(family).build()));
        given(scheduleRepository.findWithMissionByIdAndFamilyId(SCHEDULE_ID, FAMILY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verifyNoInteractions(missionRepository);
    }

    @Test
    @DisplayName("일정 날짜와 메인 장소를 기준으로 조회한 후보가 없으면 빈 목록을 반환하고 취향은 조회하지 않는다.")
    void getNearby_noCandidates() {
        stubSchedule();
        given(missionRepository.findNearbyIds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), eq(basePlace.getId()), eq(SCHEDULE_DATE))).willReturn(List.of());

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(tasteProfileService);
    }

    @Test
    @DisplayName("거절하지 않은 참여자가 선호하는 카테고리의 장소를 더 가까운 장소보다 먼저 추천한다.")
    void getNearby_sortsByParticipantTaste() {
        Mission cafe = mission(place(2L, "근처 카페", 0.5, null), Category.FOOD_CAFE, IndoorOutdoor.INDOOR, 5000);
        Mission exhibition = mission(place(3L, "먼 미술관", 2.0, null), Category.EXHIBITION, IndoorOutdoor.INDOOR, 5000);
        stubNearby(List.of(cafe, exhibition),
                List.of(nearbyParticipant(1L, ParticipantStatus.ACCEPTED),
                        nearbyParticipant(2L, ParticipantStatus.DECLINED)),
                List.of(taste(1L, Category.EXHIBITION), taste(2L, Category.FOOD_CAFE)),
                List.of());

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(placeNames(result)).containsExactly("먼 미술관", "근처 카페");
    }

    @Test
    @DisplayName("점수가 같으면 가까운 장소를 먼저 추천한다.")
    void getNearby_tieBreaksByDistance() {
        Mission far = mission(place(2L, "먼 공원", 2.0, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        Mission near = mission(place(3L, "가까운 공원", 0.5, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        stubNearby(List.of(far, near), List.of(), List.of(), List.of());

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(placeNames(result)).containsExactly("가까운 공원", "먼 공원");
    }

    @Test
    @DisplayName("같은 장소의 미션은 하나로 묶고 점수가 가장 높은 미션을 대표로 카드를 만든다.")
    void getNearby_dedupsByPlace() {
        Place sevitseom = place(2L, "세빛섬", 1.0, null);
        Mission night = mission(sevitseom, Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        Mission cafe = mission(sevitseom, Category.FOOD_CAFE, IndoorOutdoor.INDOOR, 15000);
        stubNearby(List.of(night, cafe),
                List.of(nearbyParticipant(1L, ParticipantStatus.ACCEPTED)),
                List.of(taste(1L, Category.FOOD_CAFE)),
                List.of());

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).description()).isEqualTo("세빛섬 FOOD_CAFE");
    }

    @Test
    @DisplayName("카드의 지역은 시·구까지 축약하고, 장소 이미지가 없으면 대표 미션 이미지를 쓴다.")
    void getNearby_mapsCard() {
        Mission withPlaceImage = mission(place(2L, "세빛섬", 1.0, "https://place/sevitseom"),
                Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        Mission withoutPlaceImage = mission(place(3L, "반포 한강공원", 2.0, null),
                Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        stubNearby(List.of(withPlaceImage, withoutPlaceImage), List.of(), List.of(), List.of());

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(result).extracting(ScheduleResDTO.NearbyItem::region).containsOnly("서울시 서초구");
        assertThat(result).extracting(ScheduleResDTO.NearbyItem::imageUrl)
                .containsExactly("https://place/sevitseom", "https://mission/반포 한강공원");
    }

    @Test
    @DisplayName("걸음 수 낮추기를 적용하면 취향이 맞는 먼 장소보다 가까운 장소를 먼저 추천한다.")
    void getNearby_reduceSteps() {
        Mission far = mission(place(2L, "먼 미술관", 4.0, null), Category.EXHIBITION, IndoorOutdoor.INDOOR, 5000);
        Mission near = mission(place(3L, "가까운 공원", 0.5, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 5000);
        List<ScheduleParticipant> participants = List.of(
                nearbyParticipant(1L, ParticipantStatus.ACCEPTED),
                nearbyParticipant(2L, ParticipantStatus.ACCEPTED),
                nearbyParticipant(3L, ParticipantStatus.ACCEPTED));
        List<TasteProfileDTO.MemberProfile> profiles = List.of(
                taste(1L, Category.EXHIBITION), taste(2L, Category.POPUP), taste(3L, Category.POPUP));
        stubNearby(List.of(far, near), participants, profiles, List.of(ConditionType.REDUCE_STEPS));

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(placeNames(result)).containsExactly("가까운 공원", "먼 미술관");
    }

    @Test
    @DisplayName("휴식 시간 늘리기를 적용하면 FOOD_CAFE·실내 장소를 가까운 야외 장소보다 먼저 추천한다.")
    void getNearby_moreRest() {
        Mission outdoor = mission(place(2L, "가까운 공원", 0.5, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        Mission indoor = mission(place(3L, "먼 전시관", 2.0, null), Category.EXHIBITION, IndoorOutdoor.INDOOR, 0);
        stubNearby(List.of(outdoor, indoor), List.of(), List.of(), List.of(ConditionType.MORE_REST));

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(placeNames(result)).containsExactly("먼 전시관", "가까운 공원");
    }

    @Test
    @DisplayName("예산 낮추기를 적용하면 무료 → 유료 순으로 추천하고 요금 정보가 없는 장소는 맨 뒤로 보낸다.")
    void getNearby_lowerBudget() {
        Mission unknown = mission(place(2L, "요금 모름", 0.5, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, null);
        Mission paid = mission(place(3L, "유료", 1.0, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 20000);
        Mission free = mission(place(4L, "무료", 2.0, null), Category.OUTDOOR, IndoorOutdoor.OUTDOOR, 0);
        stubNearby(List.of(unknown, paid, free), List.of(), List.of(), List.of(ConditionType.LOWER_BUDGET));

        List<ScheduleResDTO.NearbyItem> result = scheduleQueryService.getNearby(MEMBER_ID, SCHEDULE_ID);

        assertThat(placeNames(result)).containsExactly("무료", "유료", "요금 모름");
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
}
