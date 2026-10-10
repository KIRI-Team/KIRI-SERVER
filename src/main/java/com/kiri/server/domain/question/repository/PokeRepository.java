package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.question.entity.Poke;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokeRepository extends JpaRepository<Poke, Long> {
}
