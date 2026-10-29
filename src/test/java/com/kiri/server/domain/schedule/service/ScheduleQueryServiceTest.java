package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.CalendarMyStatus;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private ScheduleQueryService scheduleQueryService;

    private ScheduleReqDTO.CalendarCondition condition(int year, int month) {
        return ScheduleReqDTO.CalendarCondition.builder().year(year).month(month).build();
    }

    private Schedule schedule(Long id, LocalDate date, ScheduleStatus status, Family family) {
        return Schedule.builder().id(id).family(family).scheduleDate(date).status(status).build();
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
}
