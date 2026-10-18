package com.kiri.server.domain.family.repository;

import com.kiri.server.domain.family.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    @Query("select f.id from Family f")
    List<Long> findAllIds();
}
