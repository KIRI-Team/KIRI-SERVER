package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.family.entity.Family;
import com.kiri.server.domain.question.entity.FamilyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FamilyQuestionRepository extends JpaRepository<FamilyQuestion, Long> {
    Optional<FamilyQuestion> findByFamilyAndQuestionDate(Family family, LocalDate questionDate);
}
