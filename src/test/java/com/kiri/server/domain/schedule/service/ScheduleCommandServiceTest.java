package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleParticipantRepository;
import com.kiri.server.domain.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduleCommandServiceTest {

    private static final Long PROPOSER_ID = 1L;
    private static final Long BUCKET_ID = 42L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private MemberRepository memberRepository;
    @Mock private BucketItemRepository bucketItemRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleParticipantRepository scheduleParticipantRepository;

    private ScheduleCommandService service;

    private Family family;
    private Member proposer;
    private Member m2;
    private Member m3;

    @BeforeEach
    void setUp() {
        service = new ScheduleCommandService(memberRepository, bucketItemRepository,
                scheduleRepository, scheduleParticipantRepository, CLOCK);
        family = Family.builder().id(10L).name("가족").build();
        proposer = Member.builder().id(PROPOSER_ID).family(family).nickname("제안자").build();
        m2 = Member.builder().id(2L).family(family).nickname("엄마").build();
        m3 = Member.builder().id(3L).family(family).nickname("딸").build();
    }

    private ScheduleReqDTO.Create request(LocalDate date, LocalTime time, List<Long> ids) {
        return ScheduleReqDTO.Create.builder()
                .scheduleDate(date).scheduleTime(time).participantMemberIds(ids).requestMessage("같이 가요").build();
    }

    private BucketItem bucket(BucketStatus status, Mission mission) {
        return BucketItem.builder().id(BUCKET_ID).family(family).member(proposer).mission(mission).status(status).build();
    }

    private void stubCommon(BucketItem bucket) {
        lenient().when(memberRepository.findById(PROPOSER_ID)).thenReturn(Optional.of(proposer));
        lenient().when(bucketItemRepository.findByIdForUpdate(BUCKET_ID)).thenReturn(Optional.of(bucket));
        lenient().when(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).thenReturn(List.of(proposer, m2, m3));
        lenient().when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(memberRepository.getReferenceById(2L)).thenReturn(m2);
        lenient().when(memberRepository.getReferenceById(3L)).thenReturn(m3);
    }

    @Test
    @DisplayName("SAVED 버킷에서 일정을 만들면 제안자는 ACCEPTED, 초대자는 PENDING으로 저장하고 버킷을 SCHEDULED로 바꾼다.")
    void create_success() {
        BucketItem bucket = bucket(BucketStatus.SAVED, null);
        stubCommon(bucket);
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(BUCKET_ID), any())).willReturn(false);

        ScheduleResDTO.Created result = service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(2L, 3L)));

        assertThat(result.status()).isEqualTo(ScheduleStatus.PENDING_RESPONSE);
        assertThat(result.proposerId()).isEqualTo(PROPOSER_ID);
        assertThat(result.participantMemberIds()).containsExactly(2L, 3L);
        assertThat(result.responseDeadline()).isEqualTo(LocalDateTime.of(2026, 11, 14, 14, 0));
        assertThat(bucket.getStatus()).isEqualTo(BucketStatus.SCHEDULED);

        ArgumentCaptor<List<ScheduleParticipant>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleParticipantRepository).saveAll(captor.capture());
        List<ScheduleParticipant> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved.get(0).getMember().getId()).isEqualTo(PROPOSER_ID);
        assertThat(saved.get(0).getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
        assertThat(saved.get(1).getStatus()).isEqualTo(ParticipantStatus.PENDING);
        assertThat(saved.get(2).getStatus()).isEqualTo(ParticipantStatus.PENDING);
    }

    @Test
    @DisplayName("시작까지 24시간 이하가 남은 시점에 만들면 응답 마감은 시작 시각이 된다.")
    void create_deadlineWithin24h() {
        BucketItem bucket = bucket(BucketStatus.SAVED, null);
        stubCommon(bucket);
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(BUCKET_ID), any())).willReturn(false);

        ScheduleResDTO.Created result = service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 1), LocalTime.of(20, 0), List.of(2L)));

        assertThat(result.responseDeadline()).isEqualTo(LocalDateTime.of(2026, 11, 1, 20, 0));
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void create_memberNotFound() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("가족공간이 없으면 SCHEDULE403_1로 예외를 던진다.")
    void create_noFamily() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(Member.builder().id(PROPOSER_ID).build()));

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);
    }

    @Test
    @DisplayName("다른 가족의 버킷이면 SCHEDULE403_2로 예외를 던진다.")
    void create_bucketForbidden() {
        Family other = Family.builder().id(20L).name("다른가족").build();
        BucketItem bucket = BucketItem.builder().id(BUCKET_ID).family(other).status(BucketStatus.SAVED).build();
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(bucketItemRepository.findByIdForUpdate(BUCKET_ID)).willReturn(Optional.of(bucket));

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.BUCKET_FORBIDDEN);
    }

    @Test
    @DisplayName("SAVED가 아닌 버킷이면 SCHEDULE409_1로 예외를 던진다.")
    void create_bucketNotSchedulable() {
        BucketItem bucket = bucket(BucketStatus.SCHEDULED, null);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(bucketItemRepository.findByIdForUpdate(BUCKET_ID)).willReturn(Optional.of(bucket));

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.BUCKET_NOT_SCHEDULABLE);
    }

    @Test
    @DisplayName("지난 시각으로 만들면 SCHEDULE400_2로 예외를 던진다.")
    void create_pastDateTime() {
        BucketItem bucket = bucket(BucketStatus.SAVED, null);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(bucketItemRepository.findByIdForUpdate(BUCKET_ID)).willReturn(Optional.of(bucket));
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(BUCKET_ID), any())).willReturn(false);

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 10, 1), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.PAST_SCHEDULE_DATETIME);
    }

    @Test
    @DisplayName("기간형 행사의 운영기간 밖 날짜면 SCHEDULE400_3로 예외를 던진다.")
    void create_outOfPeriod() {
        Mission period = Mission.builder().missionType(MissionType.PERIOD)
                .startDate(LocalDate.of(2026, 11, 1)).endDate(LocalDate.of(2026, 11, 10)).build();
        BucketItem bucket = bucket(BucketStatus.SAVED, period);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(bucketItemRepository.findByIdForUpdate(BUCKET_ID)).willReturn(Optional.of(bucket));
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(BUCKET_ID), any())).willReturn(false);

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 20), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.OUT_OF_EVENT_PERIOD);
    }

    @Test
    @DisplayName("제안자 본인을 초대 대상에 넣으면 SCHEDULE400_4로 예외를 던진다.")
    void create_proposerInParticipants() {
        BucketItem bucket = bucket(BucketStatus.SAVED, null);
        stubCommon(bucket);
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(BUCKET_ID), any())).willReturn(false);

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(PROPOSER_ID, 2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.INVALID_PARTICIPANT);

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 가족이 아닌 memberId를 초대하면 SCHEDULE400_4로 예외를 던진다.")
    void create_outsiderParticipant() {
        BucketItem bucket = bucket(BucketStatus.SAVED, null);
        stubCommon(bucket);
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(BUCKET_ID), any())).willReturn(false);

        assertThatThrownBy(() -> service.create(PROPOSER_ID, BUCKET_ID,
                request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(999L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.INVALID_PARTICIPANT);
    }

    private static final Long SCHEDULE_ID = 100L;

    private Schedule schedule(ScheduleStatus status, BucketItem bucket) {
        return Schedule.builder().id(SCHEDULE_ID).proposer(proposer).status(status).bucketItem(bucket).build();
    }

    @Test
    @DisplayName("제안자가 확정 일정을 취소하면 CANCELLED가 되고 연결 버킷이 SAVED로 돌아간다.")
    void cancel_success() {
        BucketItem bucket = bucket(BucketStatus.SCHEDULED, null);
        Schedule schedule = schedule(ScheduleStatus.CONFIRMED, bucket);
        given(memberRepository.existsById(PROPOSER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        ScheduleResDTO.Cancelled result = service.cancel(PROPOSER_ID, SCHEDULE_ID);

        assertThat(result.status()).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(result.bucketItemId()).isEqualTo(BUCKET_ID);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        assertThat(bucket.getStatus()).isEqualTo(BucketStatus.SAVED);
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER404_1로 예외를 던진다.")
    void cancel_memberNotFound() {
        given(memberRepository.existsById(PROPOSER_ID)).willReturn(false);

        assertThatThrownBy(() -> service.cancel(PROPOSER_ID, SCHEDULE_ID))
                .isInstanceOf(MemberException.class);
    }

    @Test
    @DisplayName("존재하지 않는 일정이면 SCHEDULE404_2로 예외를 던진다.")
    void cancel_scheduleNotFound() {
        given(memberRepository.existsById(PROPOSER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(PROPOSER_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("제안자가 아니면 SCHEDULE403_4로 예외를 던진다.")
    void cancel_notProposer() {
        Schedule schedule = schedule(ScheduleStatus.CONFIRMED, bucket(BucketStatus.SCHEDULED, null));
        given(memberRepository.existsById(2L)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.cancel(2L, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_PROPOSER);
    }

    @Test
    @DisplayName("이미 완료된 일정이면 SCHEDULE409_3으로 예외를 던진다.")
    void cancel_notCancelable() {
        Schedule schedule = schedule(ScheduleStatus.COMPLETED, bucket(BucketStatus.COMPLETED, null));
        given(memberRepository.existsById(PROPOSER_ID)).willReturn(true);
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.cancel(PROPOSER_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_CANCELABLE);
    }
}
