package com.kiri.server.domain.term.repository;

import com.kiri.server.domain.term.entity.Term;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findAllByOrderByIdAsc();
}
