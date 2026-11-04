package com.kiri.server.domain.schedule.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.schedule.entity.Schedule;
import com.kiri.server.domain.schedule.entity.ScheduleParticipant;
import com.kiri.server.domain.schedule.enums.ParticipantStatus;
import com.kiri.server.domain.schedule.enums.ScheduleStatus;
import com.kiri.server.global.config.JpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class ScheduleRepositoryTest {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Autowired
    private TestEntityManager em;

    private Family family;
    private Family otherFamily;
    private Member me;

    @BeforeEach
    void setUp() {
        me = persistMember("캘린더나");
        family = persistFamily("캘린더가족", persistMember("캘린더아빠"));
        otherFamily = persistFamily("이웃가족", persistMember("이웃아빠"));
    }

    @Test
    @DisplayName("요청 가족의 해당 월 일정만 날짜·시간 오름차순으로 조회한다.")
    void findMonthlySchedules() {
        Schedule afternoon = persistSchedule(family, LocalDate.of(2026, 10, 3), LocalTime.of(14, 50));
        Schedule morning = persistSchedule(family, LocalDate.of(2026, 10, 3), LocalTime.of(9, 0));
        Schedule later = persistSchedule(family, LocalDate.of(2026, 10, 24), LocalTime.of(10, 0));
        persistSchedule(family, LocalDate.of(2026, 11, 1), LocalTime.of(10, 0));
        persistSchedule(otherFamily, LocalDate.of(2026, 10, 15), LocalTime.of(10, 0));
        em.flush();
        em.clear();

        List<Schedule> result = scheduleRepository
                .findByFamily_IdAndScheduleDateBetweenOrderByScheduleDateAscScheduleTimeAsc(
                        family.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(result).extracting(Schedule::getId)
                .containsExactly(morning.getId(), afternoon.getId(), later.getId());
    }

    @Test
    @DisplayName("일정 ID 목록과 회원 기준으로 내 참여 레코드만 조회한다.")
    void findMyParticipants() {
        Schedule schedule = persistSchedule(family, LocalDate.of(2026, 10, 3), LocalTime.of(14, 50));
        Member other = persistMember("다른구성원");
        ScheduleParticipant mine = persistParticipant(schedule, me, ParticipantStatus.ACCEPTED, false);
        persistParticipant(schedule, other, ParticipantStatus.PENDING, false);
        em.flush();
        em.clear();

        List<ScheduleParticipant> result = scheduleParticipantRepository
                .findBySchedule_IdInAndMember_Id(List.of(schedule.getId()), me.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(mine.getId());
        assertThat(result.get(0).getStatus()).isEqualTo(ParticipantStatus.ACCEPTED);
    }

    private Member persistMember(String nickname) {
        return em.persist(Member.builder().nickname(nickname).build());
    }

    private Family persistFamily(String name, Member owner) {
        return em.persist(Family.builder().name(name).owner(owner).build());
    }

    private Schedule persistSchedule(Family family, LocalDate date, LocalTime time) {
        return em.persist(Schedule.builder()
                .family(family)
                .scheduleDate(date)
                .scheduleTime(time)
                .status(ScheduleStatus.PENDING_RESPONSE)
                .build());
    }

    private ScheduleParticipant persistParticipant(Schedule schedule, Member member, ParticipantStatus status, boolean reconfirm) {
        return em.persist(ScheduleParticipant.builder()
                .schedule(schedule)
                .member(member)
                .status(status)
                .reconfirmRequired(reconfirm)
                .build());
    }
}
