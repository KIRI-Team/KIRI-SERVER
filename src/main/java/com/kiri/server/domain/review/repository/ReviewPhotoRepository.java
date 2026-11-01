package com.kiri.server.domain.review.repository;

import com.kiri.server.domain.review.entity.ReviewPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, Long> {

}
