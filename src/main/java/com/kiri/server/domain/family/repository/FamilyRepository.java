package com.kiri.server.domain.family.repository;

import com.kiri.server.domain.family.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, Long> {
    boolean existsByName(String name);

    boolean existsByInviteCode(String code);
}
