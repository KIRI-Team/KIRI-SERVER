package com.kiri.server.admin.review.repository;

import com.kiri.server.admin.review.entity.MissionReviewTask;
import com.kiri.server.admin.review.enums.ReviewTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionReviewTaskRepository extends JpaRepository<MissionReviewTask, Long> {

    Optional<MissionReviewTask> findFirstByExternalRecordIdAndStatusIn(Long externalRecordId, Collection<ReviewTaskStatus> statuses);

    List<MissionReviewTask> findAllByExternalRecordIdOrderByIdDesc(Long externalRecordId);

    Optional<MissionReviewTask> findFirstByExternalRecordIdAndStatusOrderByIdDesc(Long externalRecordId, ReviewTaskStatus status);
}
