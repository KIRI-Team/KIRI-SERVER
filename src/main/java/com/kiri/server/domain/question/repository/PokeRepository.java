package com.kiri.server.domain.question.repository;

import com.kiri.server.domain.member.entity.Member;
import com.kiri.server.domain.question.entity.Poke;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PokeRepository extends JpaRepository<Poke, Long> {
    boolean existsBySenderAndReceiverAndPokeDate(Member sender, Member receiver, LocalDate pokeDate);
}
