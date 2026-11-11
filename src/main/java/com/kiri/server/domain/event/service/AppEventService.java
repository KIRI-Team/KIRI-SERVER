package com.kiri.server.domain.event.service;

import com.kiri.server.domain.event.dto.AppEventReqDTO;
import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;

// 앱 행동 이벤트 저장. 이벤트 이름 목록은 아직 정하지 않았으므로 이름·속성을 검사하지 않고 그대로 저장한다.
@Service
@RequiredArgsConstructor
public class AppEventService {

    private final EntityManager em;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // 새로 저장한 건수를 돌려준다. 이미 받은 eventId는 건너뛴다.
    @Transactional
    public int record(Long memberId, AppEventReqDTO.Batch batch) {
        Long familyId = memberRepository.findById(memberId)
                .map(Member::getFamily)
                .map(Family::getId)
                .orElse(null);
        LocalDateTime receivedAt = LocalDateTime.now(clock);
        int inserted = 0;
        for (AppEventReqDTO.Event event : batch.events()) {
            inserted += em.createNativeQuery("""
                            INSERT INTO app_event (event_id, member_id, family_id, event_name, properties, platform,
                                                   app_version, occurred_at, received_at)
                            VALUES (:eventId, :memberId, :familyId, :eventName, CAST(:properties AS jsonb), :platform,
                                    :appVersion, :occurredAt, :receivedAt)
                            ON CONFLICT (event_id) DO NOTHING
                            """)
                    .setParameter("eventId", event.eventId())
                    .setParameter("memberId", memberId)
                    .setParameter("familyId", familyId)
                    .setParameter("eventName", event.eventName())
                    .setParameter("properties", event.properties() == null ? null : objectMapper.writeValueAsString(event.properties()))
                    .setParameter("platform", event.platform())
                    .setParameter("appVersion", event.appVersion())
                    .setParameter("occurredAt", event.occurredAt())
                    .setParameter("receivedAt", receivedAt)
                    .executeUpdate();
        }
        return inserted;
    }
}
