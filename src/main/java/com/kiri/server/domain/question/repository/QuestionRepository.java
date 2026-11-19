package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByFixedDayAndIsActiveTrue(Integer fixedDay);

    // 가족이 아직 받지 않은 질문 (고정 질문 제외)
    @Query("""
            select q from Question q
            where q.isActive = true
              and q.fixedDay is null
              and not exists (
                  select 1 from FamilyQuestion fq
                  where fq.family = :family and fq.question = q
              )
            """)
    List<Question> findUnassignedPoolQuestions(@Param("family") Family family);

    // 가족이 받은 질문을 마지막 배정일이 오래된 순으로
    @Query("""
            select q from FamilyQuestion fq
            join fq.question q
            where fq.family = :family
              and q.isActive = true
              and q.fixedDay is null
            group by q
            order by max(fq.questionDate) asc
            """)
    List<Question> findAssignedPoolQuestionsOrderByLastAssigned(@Param("family") Family family);
}
