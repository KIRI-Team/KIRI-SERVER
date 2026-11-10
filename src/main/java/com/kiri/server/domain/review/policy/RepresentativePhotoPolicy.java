package com.kiri.server.domain.review.policy;

import java.util.List;
import java.util.function.Function;

public final class RepresentativePhotoPolicy {

    private RepresentativePhotoPolicy() {
    }

    // 지정한 대표사진이 후보에 남아 있으면 그 사진, 아니면 후보 중 첫 사진(작성순 최초 리뷰의 첫 사진)
    public static <T> T select(Long designatedId, List<T> candidatesInDefaultOrder, Function<T, Long> idOf) {
        if (candidatesInDefaultOrder.isEmpty()) {
            return null;
        }
        if (designatedId != null) {
            for (T candidate : candidatesInDefaultOrder) {
                if (designatedId.equals(idOf.apply(candidate))) {
                    return candidate;
                }
            }
        }
        return candidatesInDefaultOrder.get(0);
    }
}
