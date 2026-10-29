package com.kiri.server.domain.bucket.repository;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BucketItemRepository extends JpaRepository<BucketItem, Long> {

    boolean existsByMemberIdAndMissionIdAndStatusIn(Long memberId, Long missionId, Collection<BucketStatus> statuses);

    @EntityGraph(attributePaths = {"mission", "member"})
    List<BucketItem> findByFamilyIdAndIdLessThanOrderByIdDesc(Long familyId, Long cursor, Pageable pageable);

    @EntityGraph(attributePaths = {"mission", "member"})
    List<BucketItem> findByFamilyIdAndMemberIdAndIdLessThanOrderByIdDesc(Long familyId, Long memberId, Long cursor,
                                                                        Pageable pageable);

    @Query("""
            SELECT b.member.id AS memberId, COUNT(b) AS bucketCount
              FROM BucketItem b
             WHERE b.family.id = :familyId
               AND b.member IS NOT NULL
             GROUP BY b.member.id
            """)
    List<SaverBucketCount> findSaverCounts(@Param("familyId") Long familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BucketItem b WHERE b.id = :id")
    Optional<BucketItem> findByIdForUpdate(@Param("id") Long id);

    interface SaverBucketCount {

        Long getMemberId();

        Long getBucketCount();
    }
}
