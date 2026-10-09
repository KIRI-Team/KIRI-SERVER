package com.kiri.server.domain.term.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "member_term",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_member_term",
                    columnNames = {"member_id", "term_id"})
        })
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

    // 동의 시점의 약관 버전
    @Column(name = "term_version", nullable = false)
    private Integer termVersion;

    // 동의일자 미동의면 null임!
    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    // 현재 버전 약관에 동의한 상태인지
    public boolean isAgreedTo(Term term) {
        return agreedAt != null && term.getVersion().equals(termVersion);
    }

    // 동의 상태 갱신
    // 이미 같은 버전에 동의한 상태로 다시 동의하면 최초 동의일자를 유지
    public void updateAgreement(boolean isAgreed, Term term) {
        if (!isAgreed) {
            this.agreedAt = null;
        } else if (!isAgreedTo(term)) {
            this.agreedAt = LocalDateTime.now();
        }
        this.termVersion = term.getVersion();
    }
}
