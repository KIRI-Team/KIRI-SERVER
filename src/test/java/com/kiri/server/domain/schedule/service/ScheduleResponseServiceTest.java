package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ResponseAction;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleResponseServiceTest {

    private static final Long PROPOSER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long SCHEDULE_ID = 100L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleParticipantRepository scheduleParticipantRepository;
    @Mock private MemberRepository memberRepository;

    private ScheduleResponseService service;

    private Member proposer;
    private Member me;

    @BeforeEach
    void setUp() {
        service = new ScheduleResponseService(scheduleRepository, scheduleParticipantRepository, memberRepository, CLOCK);
        Family family = Family.builder().id(10L).name("가족").build();
        proposer = Member.builder().id(PROPOSER_ID).family(family).nickname("제안자").build();
        me = Member.builder().id(MEMBER_ID).family(family).nickname("엄마").build();
    }

    private Schedule schedule(ScheduleStatus status, LocalDateTime deadline) {
        return Schedule.builder()
                .id(SCHEDULE_ID).proposer(proposer).status(status)
                .bucketItem(BucketItem.builder().id(42L).build())
                .scheduleDate(LocalDate.of(2026, 12, 20)).scheduleTime(LocalTime.of(14, 0))
                .responseDeadline(deadline).build();
    }

    private LocalDateTime future() {
        return LocalDateTime.now(CLOCK).plusDays(5);
    }

    private ScheduleReqDTO.Response req(ResponseAction action) {
        return ScheduleReqDTO.Response.builder().action(action).build();
    }

    @Test
    @DisplayName("초대받은 구성원이 수락하면 참여 상태가 ACCEPTED가 되고, 제안자 외 수락자가 1명이면 일정이 CONFIRMED가 된다.")
    void respond_accept_confirmed() {
        Schedule schedule = schedule(ScheduleStatus.PENDING_RESPONSE, future());
        ScheduleParticipant p = ScheduleParticipant.builder().id(5L).schedule(schedule).member(me)
                .status(ParticipantStatus.PENDING).reconfirmRequired(false).build();
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(p));
        given(scheduleParticipantRepository.countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(SCHEDULE_ID, ParticipantStatus.ACCEPTED, PROPOSER_ID))
                .willReturn(1L);

        ScheduleResDTO.Responded result = service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT));

        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
        assertThat(result.myStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
        assertThat(result.status()).isEqualTo(ScheduleStatus.CONFIRMED);
    }

    @Test
    @DisplayName("마감 전 수락자가 거절로 바꿔 제안자 외 수락자가 0명이 되면 일정이 PENDING_RESPONSE로 돌아간다.")
    void respond_decline_backToPending() {
        Schedule schedule = schedule(ScheduleStatus.CONFIRMED, future());
        ScheduleParticipant p = ScheduleParticipant.builder().id(5L).schedule(schedule).member(me)
                .status(ParticipantStatus.ACCEPTED).reconfirmRequired(false).build();
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(p));
        given(scheduleParticipantRepository.countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(SCHEDULE_ID, ParticipantStatus.ACCEPTED, PROPOSER_ID))
                .willReturn(0L);

        ScheduleResDTO.Responded result = service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.DECLINE));

        assertThat(result.myStatus()).isEqualTo(ParticipantStatus.DECLINED);
        assertThat(result.status()).isEqualTo(ScheduleStatus.PENDING_RESPONSE);
    }

    @Test
    @DisplayName("재확인 필요 상태였던 응답은 수락 시 해제된다.")
    void respond_clearsReconfirm() {
        Schedule schedule = schedule(ScheduleStatus.PENDING_RESPONSE, future());
        ScheduleParticipant p = ScheduleParticipant.builder().id(5L).schedule(schedule).member(me)
                .status(ParticipantStatus.ACCEPTED).reconfirmRequired(true).build();
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(p));
        given(scheduleParticipantRepository.countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(SCHEDULE_ID, ParticipantStatus.ACCEPTED, PROPOSER_ID))
                .willReturn(1L);

        service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT));

        assertThat(p.getReconfirmRequired()).isFalse();
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void respond_memberNotFound() {
        given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT)))
                .isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("존재하지 않는 일정이면 SCHEDULE404_2로 예외를 던진다.")
    void respond_scheduleNotFound() {
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("제안자 본인이 응답하면 SCHEDULE403_3으로 예외를 던진다.")
    void respond_proposerCannotRespond() {
        Schedule schedule = schedule(ScheduleStatus.PENDING_RESPONSE, future());
        given(memberRepository.existsById(PROPOSER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.respond(PROPOSER_ID, SCHEDULE_ID, req(ResponseAction.DECLINE)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_INVITED);
    }

    @Test
    @DisplayName("초대받지 않은 구성원이 응답하면 SCHEDULE403_3으로 예외를 던진다.")
    void respond_notInvited() {
        Schedule schedule = schedule(ScheduleStatus.PENDING_RESPONSE, future());
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_INVITED);
    }

    @Test
    @DisplayName("응답 마감이 지난 일정이면 SCHEDULE409_2로 예외를 던진다.")
    void respond_deadlinePassed() {
        Schedule schedule = schedule(ScheduleStatus.PENDING_RESPONSE, LocalDateTime.now(CLOCK).minusHours(1));
        ScheduleParticipant p = ScheduleParticipant.builder().id(5L).schedule(schedule).member(me)
                .status(ParticipantStatus.PENDING).reconfirmRequired(false).build();
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.RESPONSE_CLOSED);
    }

    @Test
    @DisplayName("마감이 지난 PENDING 일정에 수락자가 없으면 finalizeExpired로 NOT_CONFIRMED가 된다.")
    void finalizeExpired_notConfirmed() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        Schedule schedule = schedule(ScheduleStatus.PENDING_RESPONSE, now.minusHours(1));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(SCHEDULE_ID, ParticipantStatus.ACCEPTED, PROPOSER_ID))
                .willReturn(0L);

        service.finalizeExpired(SCHEDULE_ID, now);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.NOT_CONFIRMED);
    }

    @Test
    @DisplayName("PENDING이 아닌 일정은 finalizeExpired에서 건너뛴다.")
    void finalizeExpired_skipNonPending() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        Schedule schedule = schedule(ScheduleStatus.CONFIRMED, now.minusHours(1));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        service.finalizeExpired(SCHEDULE_ID, now);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.CONFIRMED);
    }

    @Test
    @DisplayName("취소된 일정이면 SCHEDULE409_2로 예외를 던진다.")
    void respond_cancelled() {
        Schedule schedule = schedule(ScheduleStatus.CANCELLED, future());
        ScheduleParticipant p = ScheduleParticipant.builder().id(5L).schedule(schedule).member(me)
                .status(ParticipantStatus.PENDING).reconfirmRequired(false).build();
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleParticipantRepository.findBySchedule_IdAndMember_IdForUpdate(SCHEDULE_ID, MEMBER_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.respond(MEMBER_ID, SCHEDULE_ID, req(ResponseAction.ACCEPT)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.RESPONSE_CLOSED);
    }
}
