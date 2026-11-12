package com.kiri.server.domain.character.repository;

import com.kiri.server.domain.character.entity.Character;
import com.kiri.server.domain.member.enums.FamilyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    Optional<Character> findByRole(FamilyRole role);
}
