package com.kiri.server.domain.review.repository;

import com.kiri.server.domain.review.entity.ReviewPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, Long> {

    List<ReviewPhoto> findByReview_IdInOrderByReview_IdAscDisplayOrderAsc(List<Long> reviewIds);

    @Query("""
            select p from ReviewPhoto p
            join fetch p.review r
            where r.schedule.id in :scheduleIds
            order by r.schedule.id asc, r.createdAt asc, r.id asc, p.displayOrder asc
            """)
    List<ReviewPhoto> findAllByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);
}
