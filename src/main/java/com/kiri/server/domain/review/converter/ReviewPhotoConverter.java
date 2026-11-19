package com.kiri.server.domain.review.converter;

import com.kiri.server.domain.review.entity.Review;
import com.kiri.server.domain.review.entity.ReviewPhoto;

public class ReviewPhotoConverter {

    public static ReviewPhoto toReviewPhoto(Review review, String imageKey, Integer displayOrder) {
        return ReviewPhoto.builder()
                .review(review)
                .imageKey(imageKey)
                .displayOrder(displayOrder)
                .build();
    }
}
