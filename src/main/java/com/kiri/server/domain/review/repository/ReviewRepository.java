package com.kiri.server.domain.review.repository;

import com.kiri.server.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsBySchedule_IdAndScheduleParticipant_Id(Long scheduleId, Long scheduleParticipantId);

    @Query("select r from Review r " +
            "join fetch r.scheduleParticipant sp " +
            "left join fetch sp.member " +
            "where r.id = :reviewId and r.schedule.id = :scheduleId")
    Optional<Review> findByIdAndScheduleIdWithWriter(@Param("reviewId") Long reviewId, @Param("scheduleId") Long scheduleId);

    @Query("select r from Review r " +
            "join fetch r.scheduleParticipant sp " +
            "left join fetch sp.member " +
            "where r.schedule.id = :scheduleId " +
            "order by r.createdAt asc")
    List<Review> findAllByScheduleIdWithWriter(@Param("scheduleId") Long scheduleId);

    @Query("""
    SELECT r
    FROM Review r
    JOIN FETCH r.scheduleParticipant sp
    JOIN FETCH sp.member
    JOIN FETCH r.schedule s
    JOIN FETCH s.bucketItem bi
    JOIN FETCH bi.mission m
    WHERE sp.member.id IN :memberIds
      AND s.status = com.kiri.server.domain.schedule.enums.ScheduleStatus.COMPLETED
    """)
    List<Review> findAllCompletedReviewsByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    @Query("""
            SELECT AVG(r.starRating) AS averageRating, COUNT(r) AS reviewCount
            FROM Review r
            WHERE r.schedule.bucketItem.mission.id = :missionId
              AND r.starRating IS NOT NULL
            """)
    MissionRating findMissionRating(@Param("missionId") Long missionId);

    interface MissionRating {

        Double getAverageRating();

        long getReviewCount();
    }
}
