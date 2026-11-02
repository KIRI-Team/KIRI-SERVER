package com.kiri.server.domain.review.repository;

import com.kiri.server.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsBySchedule_IdAndScheduleParticipant_Id(Long scheduleId, Long scheduleParticipantId);

    @Query("select r from Review r " +
            "join fetch r.scheduleParticipant sp " +
            "left join fetch sp.member " +
            "where r.schedule.id = :scheduleId " +
            "order by r.createdAt asc")
    List<Review> findAllByScheduleIdWithWriter(@Param("scheduleId") Long scheduleId);
}
