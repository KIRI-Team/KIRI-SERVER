package com.kiri.server.domain.review.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class RepresentativePhotoPolicyTest {

    private final Function<Long, Long> idOf = Function.identity();

    @Test
    @DisplayName("지정한 대표사진이 후보에 있으면 그 사진을 선택한다")
    void selectsDesignatedPhoto() {
        Long selected = RepresentativePhotoPolicy.select(2L, List.of(1L, 2L, 3L), idOf);

        assertThat(selected).isEqualTo(2L);
    }

    @Test
    @DisplayName("지정한 대표사진이 없으면 후보 중 첫 사진을 선택한다")
    void selectsFirstPhotoWhenNothingDesignated() {
        Long selected = RepresentativePhotoPolicy.select(null, List.of(1L, 2L, 3L), idOf);

        assertThat(selected).isEqualTo(1L);
    }

    @Test
    @DisplayName("지정한 대표사진이 후보에서 사라졌으면 후보 중 첫 사진을 선택한다")
    void selectsFirstPhotoWhenDesignatedPhotoIsGone() {
        Long selected = RepresentativePhotoPolicy.select(99L, List.of(1L, 2L, 3L), idOf);

        assertThat(selected).isEqualTo(1L);
    }

    @Test
    @DisplayName("후보가 하나도 없으면 null을 반환한다")
    void returnsNullWhenNoCandidates() {
        Long selected = RepresentativePhotoPolicy.select(2L, List.of(), idOf);

        assertThat(selected).isNull();
    }
}
