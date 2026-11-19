package com.kiri.server.domain.term.repository;

import com.kiri.server.domain.term.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TermRepository extends JpaRepository<Term, Long> {


    List<Term> findAllByOrderByIdAsc();
}
