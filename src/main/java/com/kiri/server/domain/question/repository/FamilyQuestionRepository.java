package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import com.kiri.server.domain.question.enums.TasteType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FamilyQuestionRepository extends JpaRepository<FamilyQuestion, Long> {
    Optional<FamilyQuestion> findByFamilyAndQuestionDate(Family family, LocalDate questionDate);

    boolean existsByFamilyAndQuestionDate(Family family, LocalDate questionDate);

    // 취향 종류별 배정 횟수
    @Query("""
            select q.tasteType as tasteType, count(fq) as assignedCount
            from FamilyQuestion fq
            join fq.question q
            where fq.family = :family
            group by q.tasteType
            """)
    List<TasteTypeCount> countAssignedByTasteType(@Param("family") Family family);

    interface TasteTypeCount {
        TasteType getTasteType();

        Long getAssignedCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select fq
        from FamilyQuestion fq
        where fq.id = :id
       """)
    Optional<FamilyQuestion> findByIdForUpdate(@Param("id") Long id);
}
