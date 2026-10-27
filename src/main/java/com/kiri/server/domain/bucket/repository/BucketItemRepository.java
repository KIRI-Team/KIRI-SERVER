package com.kiri.server.domain.bucket.repository;

import com.kiri.server.domain.bucket.entity.BucketItem;
import com.kiri.server.domain.bucket.enums.BucketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface BucketItemRepository extends JpaRepository<BucketItem, Long> {

    boolean existsByMemberIdAndMissionIdAndStatusIn(Long memberId, Long missionId, Collection<BucketStatus> statuses);
}
