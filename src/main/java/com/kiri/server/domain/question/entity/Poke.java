package com.kiri.server.domain.question.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "poke",
        uniqueConstraints = {
                // 같은 사람에게 하루 한 번만 찌르기 가능
                @UniqueConstraint(name = "uk_poke_sender_receiver_date", columnNames = {"sender_id", "receiver_id", "poke_date"})
        }
)
public class Poke extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "poke_id")
    private Long id;

    // 찌른 날짜 (하루 1회 제한 판단용, 한국 시간 기준)
    @Column(name = "poke_date", nullable = false)
    private LocalDate pokeDate;

    // 찌른 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    // 찔린 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;
}
