package com.kiri.server.domain.term.entity;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.term.converter.TermConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;

class MemberTermTest {

    private final Member member = Member.builder().build();
    private final Term termV1 = Term.builder()
            .title("서비스 이용약관").shortDescription("요약").description("본문")
            .isRequired(true).version(1).build();
    private final Term termV2 = Term.builder()
            .title("서비스 이용약관").shortDescription("요약").description("본문")
            .isRequired(true).version(2).build();

    @Test
    @DisplayName("현재 버전에 동의한 기록이면 동의한 것으로 판단한다.")
    void isAgreedTo_sameVersion() {
        MemberTerm memberTerm = TermConverter.toMemberTerm(member, termV1, true);

        assertThat(memberTerm.isAgreedTo(termV1)).isTrue();
    }

    @Test
    @DisplayName("약관 버전이 올라가면 이전 버전 동의는 미동의로 판단한다.")
    void isAgreedTo_oldVersion() {
        MemberTerm memberTerm = TermConverter.toMemberTerm(member, termV1, true);

        assertThat(memberTerm.isAgreedTo(termV2)).isFalse();
    }

    @Test
    @DisplayName("같은 버전에 다시 동의하면 기존 동의 시각을 유지한다.")
    void updateAgreement_sameVersionKeepsAgreedAt() {
        MemberTerm memberTerm = TermConverter.toMemberTerm(member, termV1, true);
        LocalDateTime firstAgreedAt = memberTerm.getAgreedAt();

        memberTerm.updateAgreement(true, termV1);

        assertThat(memberTerm.getAgreedAt()).isEqualTo(firstAgreedAt);
    }
}
