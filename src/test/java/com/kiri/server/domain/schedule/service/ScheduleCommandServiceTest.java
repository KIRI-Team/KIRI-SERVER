package com.kiri.server.domain.schedule.service;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import com.kiri.server.domain.bucket.repository.BucketItemRepository;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.exception.MemberException;
import com.kiri.server.domain.member.repository.MemberRepository;
import com.kiri.server.domain.mission.entity.FamilyMissionRecommendation;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.enums.MissionStatus;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.repository.FamilyMissionRecommendationRepository;
import com.kiri.server.domain.schedule.dto.ScheduleReqDTO;
import com.kiri.server.domain.schedule.dto.ScheduleResDTO;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleCondition;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ConditionType;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.domain.schedule.exception.ScheduleException;
import com.kiri.server.domain.schedule.exception.code.ScheduleErrorCode;
import com.kiri.server.domain.schedule.repository.ScheduleConditionRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ScheduleCommandServiceTest {

    private static final Long PROPOSER_ID = 1L;
    private static final Long BUCKET_ID = 42L;
    private static final Long MISSION_ID = 55L;
    private static final Long NEW_BUCKET_ID = 77L;
    private static final Long EXISTING_BUCKET_ID = 88L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private MemberRepository memberRepository;
    @Mock private BucketItemRepository bucketItemRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ScheduleParticipantRepository scheduleParticipantRepository;
    @Mock private ScheduleConditionRepository scheduleConditionRepository;
    @Mock private FamilyMissionRecommendationRepository familyMissionRecommendationRepository;

    private ScheduleCommandService service;

    private Family family;
    private Member proposer;
    private Member m2;
    private Member m3;

    @BeforeEach
    void setUp() {
        service = new ScheduleCommandService(memberRepository, bucketItemRepository,
                scheduleRepository, scheduleParticipantRepository, scheduleConditionRepository,
                familyMissionRecommendationRepository, CLOCK);
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

    private Schedule updatableSchedule(ScheduleStatus status, LocalDate date, LocalTime time) {
        return Schedule.builder().id(SCHEDULE_ID).proposer(proposer).status(status)
                .bucketItem(bucket(BucketStatus.SCHEDULED, null))
                .scheduleDate(date).scheduleTime(time)
                .responseDeadline(LocalDateTime.of(2026, 12, 19, 14, 0)).build();
    }

    private ScheduleParticipant participant(Schedule schedule, Member member, ParticipantStatus status) {
        return ScheduleParticipant.builder().schedule(schedule).member(member)
                .status(status).reconfirmRequired(false).build();
    }

    private ScheduleReqDTO.Update updateReq(LocalDate date, LocalTime time, List<Long> ids) {
        return ScheduleReqDTO.Update.builder()
                .scheduleDate(date).scheduleTime(time).participantMemberIds(ids).requestMessage("수정").build();
    }

    @Test
    @DisplayName("날짜를 바꾸면 기존 수락자는 재확인 필요가 되고 일정이 PENDING_RESPONSE로 돌아간다.")
    void update_coreChange() {
        Schedule schedule = updatableSchedule(ScheduleStatus.CONFIRMED, LocalDate.of(2026, 12, 20), LocalTime.of(14, 0));
        ScheduleParticipant m2p = participant(schedule, m2, ParticipantStatus.ACCEPTED);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(proposer, m2, m3));
        given(scheduleParticipantRepository.findBySchedule_Id(SCHEDULE_ID))
                .willReturn(List.of(participant(schedule, proposer, ParticipantStatus.ACCEPTED), m2p));
        given(memberRepository.getReferenceById(3L)).willReturn(m3);
        given(scheduleParticipantRepository.countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(
                SCHEDULE_ID, ParticipantStatus.ACCEPTED, PROPOSER_ID)).willReturn(0L);

        ScheduleResDTO.Updated result = service.update(PROPOSER_ID, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 12, 21), LocalTime.of(14, 0), List.of(2L, 3L)));

        assertThat(result.scheduleDate()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(result.status()).isEqualTo(ScheduleStatus.PENDING_RESPONSE);
        assertThat(result.participantMemberIds()).containsExactly(2L, 3L);
        assertThat(m2p.getReconfirmRequired()).isTrue();
        verify(scheduleParticipantRepository).saveAll(any());
    }

    @Test
    @DisplayName("참여자를 교체하면 빠진 참여자는 삭제되고 새 참여자는 미응답으로 추가된다.")
    void update_replaceParticipants() {
        Schedule schedule = updatableSchedule(ScheduleStatus.CONFIRMED, LocalDate.of(2026, 12, 20), LocalTime.of(14, 0));
        ScheduleParticipant m2p = participant(schedule, m2, ParticipantStatus.ACCEPTED);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(proposer, m2, m3));
        given(scheduleParticipantRepository.findBySchedule_Id(SCHEDULE_ID))
                .willReturn(List.of(participant(schedule, proposer, ParticipantStatus.ACCEPTED), m2p));
        given(memberRepository.getReferenceById(3L)).willReturn(m3);
        given(scheduleParticipantRepository.countBySchedule_IdAndStatusAndReconfirmRequiredFalseAndMember_IdNot(
                SCHEDULE_ID, ParticipantStatus.ACCEPTED, PROPOSER_ID)).willReturn(0L);

        ScheduleResDTO.Updated result = service.update(PROPOSER_ID, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 12, 20), LocalTime.of(14, 0), List.of(3L)));

        assertThat(result.participantMemberIds()).containsExactly(3L);
        ArgumentCaptor<List<ScheduleParticipant>> removed = ArgumentCaptor.forClass(List.class);
        verify(scheduleParticipantRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).containsExactly(m2p);
        verify(scheduleParticipantRepository).saveAll(any());
    }

    @Test
    @DisplayName("제안자가 아니면 SCHEDULE403_4로 예외를 던진다.")
    void update_notProposer() {
        Schedule schedule = updatableSchedule(ScheduleStatus.CONFIRMED, LocalDate.of(2026, 12, 20), LocalTime.of(14, 0));
        given(memberRepository.findById(2L)).willReturn(Optional.of(m2));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.update(2L, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 12, 21), LocalTime.of(14, 0), List.of(3L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_PROPOSER);
    }

    @Test
    @DisplayName("취소된 일정은 SCHEDULE409_4로 예외를 던진다.")
    void update_notUpdatable() {
        Schedule schedule = updatableSchedule(ScheduleStatus.CANCELLED, LocalDate.of(2026, 12, 20), LocalTime.of(14, 0));
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.update(PROPOSER_ID, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 12, 21), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_UPDATABLE);
    }

    @Test
    @DisplayName("활동이 시작된 일정은 SCHEDULE409_4로 예외를 던진다.")
    void update_alreadyStarted() {
        Schedule schedule = updatableSchedule(ScheduleStatus.CONFIRMED, LocalDate.of(2026, 10, 20), LocalTime.of(14, 0));
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.update(PROPOSER_ID, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 12, 21), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_UPDATABLE);
    }

    @Test
    @DisplayName("존재하지 않는 일정이면 SCHEDULE404_2로 예외를 던진다.")
    void update_scheduleNotFound() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(PROPOSER_ID, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 12, 21), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("지난 시각으로 수정하면 SCHEDULE400_2로 예외를 던진다.")
    void update_pastDateTime() {
        Schedule schedule = updatableSchedule(ScheduleStatus.CONFIRMED, LocalDate.of(2026, 12, 20), LocalTime.of(14, 0));
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.update(PROPOSER_ID, SCHEDULE_ID,
                updateReq(LocalDate.of(2026, 10, 1), LocalTime.of(14, 0), List.of(2L))))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.PAST_SCHEDULE_DATETIME);
    }

    private Schedule conditionSchedule(ScheduleStatus status, Family owner) {
        return conditionSchedule(status, owner, LocalDate.of(2026, 11, 1));
    }

    private Schedule conditionSchedule(ScheduleStatus status, Family owner, LocalDate date) {
        return Schedule.builder().id(SCHEDULE_ID).family(owner).status(status).scheduleDate(date).build();
    }

    private void stubFinalParticipant(boolean finalParticipant) {
        given(scheduleParticipantRepository.existsBySchedule_IdAndMember_IdAndStatusAndReconfirmRequiredFalse(
                SCHEDULE_ID, PROPOSER_ID, ParticipantStatus.ACCEPTED)).willReturn(finalParticipant);
    }

    private ScheduleReqDTO.UpdateConditions conditionsReq(ConditionType... types) {
        return ScheduleReqDTO.UpdateConditions.builder().conditions(List.of(types)).build();
    }

    private ScheduleCondition condition(Schedule schedule, ConditionType type) {
        return ScheduleCondition.builder().schedule(schedule).conditionType(type).build();
    }

    @SuppressWarnings("unchecked")
    private List<ScheduleCondition> capturedDeleted() {
        ArgumentCaptor<List<ScheduleCondition>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleConditionRepository).deleteAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ScheduleCondition> capturedSaved() {
        ArgumentCaptor<List<ScheduleCondition>> captor = ArgumentCaptor.forClass(List.class);
        verify(scheduleConditionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("회원이 없으면 컨디션을 적용하지 않고 MEMBER404_1로 예외를 던진다.")
    void updateConditions_memberNotFound() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID, conditionsReq()))
                .isInstanceOf(MemberException.class);

        verifyNoInteractions(scheduleRepository, scheduleConditionRepository);
    }

    @Test
    @DisplayName("가족공간이 없으면 컨디션을 적용하지 않고 SCHEDULE403_1로 예외를 던진다.")
    void updateConditions_noFamily() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(Member.builder().id(PROPOSER_ID).build()));

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID, conditionsReq()))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.FAMILY_REQUIRED);

        verifyNoInteractions(scheduleRepository, scheduleConditionRepository);
    }

    @Test
    @DisplayName("다른 가족의 일정에 컨디션을 적용하면 SCHEDULE404_2로 예외를 던지고 조건은 건드리지 않는다.")
    void updateConditions_otherFamily() {
        Family otherFamily = Family.builder().id(99L).build();
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID))
                .willReturn(Optional.of(conditionSchedule(ScheduleStatus.CONFIRMED, otherFamily)));

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.SCHEDULE_NOT_FOUND);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("최종 참여자가 아니면 컨디션을 적용하지 않고 SCHEDULE403_5로 예외를 던진다.")
    void updateConditions_notFinalParticipant() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID))
                .willReturn(Optional.of(conditionSchedule(ScheduleStatus.CONFIRMED, family)));
        stubFinalParticipant(false);

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_FINAL_PARTICIPANT);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("아직 확정되지 않은 일정에 컨디션을 적용하면 SCHEDULE409_5로 예외를 던지고 조건은 건드리지 않는다.")
    void updateConditions_notConfirmed() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID))
                .willReturn(Optional.of(conditionSchedule(ScheduleStatus.PENDING_RESPONSE, family)));
        stubFinalParticipant(true);

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.CONDITION_NOT_EDITABLE);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("취소된 일정에 컨디션을 적용하면 SCHEDULE409_5로 예외를 던지고 조건은 건드리지 않는다.")
    void updateConditions_notEditable() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID))
                .willReturn(Optional.of(conditionSchedule(ScheduleStatus.CANCELLED, family)));
        stubFinalParticipant(true);

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.CONDITION_NOT_EDITABLE);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("날짜가 지난 일정에 컨디션을 적용하면 SCHEDULE409_5로 예외를 던지고 조건은 건드리지 않는다.")
    void updateConditions_pastSchedule() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(
                conditionSchedule(ScheduleStatus.CONFIRMED, family, LocalDate.of(2026, 10, 31))));
        stubFinalParticipant(true);

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.CONDITION_NOT_EDITABLE);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("아직 당일이 아닌 일정에 컨디션을 적용하면 SCHEDULE409_5로 예외를 던지고 조건은 건드리지 않는다.")
    void updateConditions_futureSchedule() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(
                conditionSchedule(ScheduleStatus.CONFIRMED, family, LocalDate.of(2026, 11, 2))));
        stubFinalParticipant(true);

        assertThatThrownBy(() -> service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST)))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.CONDITION_NOT_EDITABLE);

        verifyNoInteractions(scheduleConditionRepository);
    }

    @Test
    @DisplayName("당일 일정에는 컨디션을 적용할 수 있다.")
    void updateConditions_sameDay() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(
                conditionSchedule(ScheduleStatus.CONFIRMED, family, LocalDate.of(2026, 11, 1))));
        stubFinalParticipant(true);
        given(scheduleConditionRepository.findBySchedule_Id(SCHEDULE_ID)).willReturn(List.of());

        service.updateConditions(PROPOSER_ID, SCHEDULE_ID, conditionsReq(ConditionType.MORE_REST));

        assertThat(capturedSaved()).extracting(ScheduleCondition::getConditionType)
                .containsExactly(ConditionType.MORE_REST);
    }

    @Test
    @DisplayName("컨디션을 적용하면 요청에서 빠진 조건만 삭제하고 새 조건만 추가하며 겹치는 조건은 유지한다.")
    void updateConditions_appliesDiff() {
        Schedule schedule = conditionSchedule(ScheduleStatus.CONFIRMED, family);
        ScheduleCondition reduceSteps = condition(schedule, ConditionType.REDUCE_STEPS);
        ScheduleCondition moreRest = condition(schedule, ConditionType.MORE_REST);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        stubFinalParticipant(true);
        given(scheduleConditionRepository.findBySchedule_Id(SCHEDULE_ID)).willReturn(List.of(reduceSteps, moreRest));

        service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST, ConditionType.LOWER_BUDGET));

        assertThat(capturedDeleted()).containsExactly(reduceSteps);
        assertThat(capturedSaved()).extracting(ScheduleCondition::getConditionType)
                .containsExactly(ConditionType.LOWER_BUDGET);
    }

    @Test
    @DisplayName("빈 목록으로 컨디션을 적용하면 기존 조건을 모두 삭제하고 아무것도 추가하지 않는다.")
    void updateConditions_clearAll() {
        Schedule schedule = conditionSchedule(ScheduleStatus.CONFIRMED, family);
        ScheduleCondition reduceSteps = condition(schedule, ConditionType.REDUCE_STEPS);
        ScheduleCondition moreRest = condition(schedule, ConditionType.MORE_REST);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        stubFinalParticipant(true);
        given(scheduleConditionRepository.findBySchedule_Id(SCHEDULE_ID)).willReturn(List.of(reduceSteps, moreRest));

        service.updateConditions(PROPOSER_ID, SCHEDULE_ID, conditionsReq());

        assertThat(capturedDeleted()).containsExactlyInAnyOrder(reduceSteps, moreRest);
        assertThat(capturedSaved()).isEmpty();
    }

    @Test
    @DisplayName("같은 조건을 중복으로 보내도 컨디션은 한 번만 추가한다.")
    void updateConditions_deduplicates() {
        Schedule schedule = conditionSchedule(ScheduleStatus.CONFIRMED, family);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(scheduleRepository.findByIdForUpdate(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        stubFinalParticipant(true);
        given(scheduleConditionRepository.findBySchedule_Id(SCHEDULE_ID)).willReturn(List.of());

        service.updateConditions(PROPOSER_ID, SCHEDULE_ID,
                conditionsReq(ConditionType.MORE_REST, ConditionType.MORE_REST));

        assertThat(capturedDeleted()).isEmpty();
        assertThat(capturedSaved()).extracting(ScheduleCondition::getConditionType)
                .containsExactly(ConditionType.MORE_REST);
    }

    private Mission weeklyMission(MissionStatus status, LocalDate endDate) {
        return Mission.builder()
                .id(MISSION_ID).status(status).missionType(MissionType.PERMANENT).endDate(endDate).build();
    }

    private FamilyMissionRecommendation recommendation(Mission mission) {
        return FamilyMissionRecommendation.builder()
                .id(1L).family(family).mission(mission)
                .recommendationScore(java.math.BigDecimal.ONE).recommendationRank(1).build();
    }

    private ScheduleReqDTO.Create weeklyRequest() {
        return request(LocalDate.of(2026, 11, 15), LocalTime.of(14, 0), List.of(2L, 3L));
    }

    @Test
    @DisplayName("주간 미션에서 일정을 만들 때 제안자의 SAVED 버킷이 없으면 제안자를 저장자로 버킷을 새로 만들고 일정을 생성한다.")
    void createFromWeeklyMission_createsBucket() {
        Mission mission = weeklyMission(MissionStatus.ACTIVE, null);
        BucketItem newBucket = BucketItem.builder().id(NEW_BUCKET_ID).family(family).member(proposer)
                .mission(mission).status(BucketStatus.SAVED).build();
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(10L))
                .willReturn(Optional.of(recommendation(mission)));
        given(scheduleRepository.existsByFamily_IdAndBucketItem_Mission_IdAndStatusIn(eq(10L), eq(MISSION_ID), any()))
                .willReturn(false);
        given(bucketItemRepository.findFirstByMember_IdAndMission_IdAndStatus(PROPOSER_ID, MISSION_ID, BucketStatus.SAVED))
                .willReturn(Optional.empty());
        given(bucketItemRepository.save(any(BucketItem.class))).willReturn(newBucket);
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(NEW_BUCKET_ID), any())).willReturn(false);
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(proposer, m2, m3));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));
        given(memberRepository.getReferenceById(2L)).willReturn(m2);
        given(memberRepository.getReferenceById(3L)).willReturn(m3);

        ScheduleResDTO.Created result = service.createFromWeeklyMission(PROPOSER_ID, MISSION_ID, weeklyRequest());

        assertThat(result.status()).isEqualTo(ScheduleStatus.PENDING_RESPONSE);
        assertThat(result.proposerId()).isEqualTo(PROPOSER_ID);
        assertThat(result.participantMemberIds()).containsExactly(2L, 3L);
        assertThat(result.bucketItemId()).isEqualTo(NEW_BUCKET_ID);
        assertThat(newBucket.getStatus()).isEqualTo(BucketStatus.SCHEDULED);
        verify(bucketItemRepository).save(any(BucketItem.class));
    }

    @Test
    @DisplayName("주간 미션에서 일정을 만들 때 제안자의 SAVED 버킷이 있으면 새로 만들지 않고 그 버킷에 연결한다.")
    void createFromWeeklyMission_connectsExistingBucket() {
        Mission mission = weeklyMission(MissionStatus.ACTIVE, null);
        BucketItem existing = BucketItem.builder().id(EXISTING_BUCKET_ID).family(family).member(proposer)
                .mission(mission).status(BucketStatus.SAVED).build();
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(10L))
                .willReturn(Optional.of(recommendation(mission)));
        given(scheduleRepository.existsByFamily_IdAndBucketItem_Mission_IdAndStatusIn(eq(10L), eq(MISSION_ID), any()))
                .willReturn(false);
        given(bucketItemRepository.findFirstByMember_IdAndMission_IdAndStatus(PROPOSER_ID, MISSION_ID, BucketStatus.SAVED))
                .willReturn(Optional.of(existing));
        given(bucketItemRepository.findByIdForUpdate(EXISTING_BUCKET_ID)).willReturn(Optional.of(existing));
        given(scheduleRepository.existsByBucketItem_IdAndStatusIn(eq(EXISTING_BUCKET_ID), any())).willReturn(false);
        given(memberRepository.findAllByFamilyAndDeletedAtIsNull(family)).willReturn(List.of(proposer, m2, m3));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));
        given(memberRepository.getReferenceById(2L)).willReturn(m2);
        given(memberRepository.getReferenceById(3L)).willReturn(m3);

        ScheduleResDTO.Created result = service.createFromWeeklyMission(PROPOSER_ID, MISSION_ID, weeklyRequest());

        assertThat(result.bucketItemId()).isEqualTo(EXISTING_BUCKET_ID);
        assertThat(existing.getStatus()).isEqualTo(BucketStatus.SCHEDULED);
        verify(bucketItemRepository, never()).save(any(BucketItem.class));
    }

    @Test
    @DisplayName("배정된 주간 미션이 없으면 SCHEDULE409_8로 예외를 던진다.")
    void createFromWeeklyMission_noAssignedMission() {
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(10L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFromWeeklyMission(PROPOSER_ID, MISSION_ID, weeklyRequest()))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_ASSIGNED_WEEKLY_MISSION);
        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("요청한 missionId가 배정된 주간 미션과 다르면 SCHEDULE409_8로 예외를 던진다.")
    void createFromWeeklyMission_missionMismatch() {
        Mission assigned = weeklyMission(MissionStatus.ACTIVE, null);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(10L))
                .willReturn(Optional.of(recommendation(assigned)));

        assertThatThrownBy(() -> service.createFromWeeklyMission(PROPOSER_ID, 999L, weeklyRequest()))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.NOT_ASSIGNED_WEEKLY_MISSION);
        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("배정된 주간 미션이 종료 상태면 SCHEDULE409_9로 예외를 던진다.")
    void createFromWeeklyMission_missionNotAvailable() {
        Mission mission = weeklyMission(MissionStatus.ENDED, null);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(10L))
                .willReturn(Optional.of(recommendation(mission)));

        assertThatThrownBy(() -> service.createFromWeeklyMission(PROPOSER_ID, MISSION_ID, weeklyRequest()))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.WEEKLY_MISSION_NOT_AVAILABLE);
        verifyNoInteractions(scheduleParticipantRepository);
    }

    @Test
    @DisplayName("같은 주간 미션에 이미 연결된 활성 일정이 있으면 SCHEDULE409_10으로 예외를 던진다.")
    void createFromWeeklyMission_alreadyLinked() {
        Mission mission = weeklyMission(MissionStatus.ACTIVE, null);
        given(memberRepository.findById(PROPOSER_ID)).willReturn(Optional.of(proposer));
        given(familyMissionRecommendationRepository.findFirstByFamilyIdOrderByRecommendationRankAsc(10L))
                .willReturn(Optional.of(recommendation(mission)));
        given(scheduleRepository.existsByFamily_IdAndBucketItem_Mission_IdAndStatusIn(eq(10L), eq(MISSION_ID), any()))
                .willReturn(true);

        assertThatThrownBy(() -> service.createFromWeeklyMission(PROPOSER_ID, MISSION_ID, weeklyRequest()))
                .isInstanceOf(ScheduleException.class)
                .extracting(e -> ((ScheduleException) e).getErrorCode())
                .isEqualTo(ScheduleErrorCode.WEEKLY_SCHEDULE_ALREADY_EXISTS);
        verify(bucketItemRepository, never()).save(any(BucketItem.class));
    }
}
