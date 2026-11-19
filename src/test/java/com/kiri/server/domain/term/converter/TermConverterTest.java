package com.kiri.server.domain.term.converter;

import com.kiri.server.domain.term.dto.TermResDTO;
import com.kiri.server.domain.term.entity.Term;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TermConverterTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 10, 5, 15, 30);

    @Test
    @DisplayName("수정된 적 없는 약관은 생성일자를 최종 수정일로 반환한다.")
    void toInfo_notUpdated() {
        Term term = createTerm();
        ReflectionTestUtils.setField(term, "createdAt", CREATED_AT);

        TermResDTO.Info info = TermConverter.toInfo(term);

        assertThat(info.updatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("수정된 약관은 수정일자를 최종 수정일로 반환한다.")
    void toInfo_updated() {
        Term term = createTerm();
        ReflectionTestUtils.setField(term, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(term, "updatedAt", UPDATED_AT);

        TermResDTO.Info info = TermConverter.toInfo(term);

        assertThat(info.updatedAt()).isEqualTo(UPDATED_AT);
    }

    private Term createTerm() {
        return Term.builder()
                .id(1L)
                .title("서비스 이용약관")
                .shortDescription("서비스 이용약관 요약")
                .description("서비스 이용약관 본문")
                .isRequired(true)
                .build();
    }
}
