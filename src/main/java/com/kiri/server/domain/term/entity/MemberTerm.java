package com.kiri.server.domain.term.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_term",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_term", columnNames = {"member_id", "term_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberTerm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_term_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    public void updateAgreedAt(LocalDateTime agreedAt) {
        this.agreedAt = agreedAt;
    }
}
