package com.kiri.server.admin.auth.repository;

import com.kiri.server.admin.auth.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByIdAndEnabledTrue(Long id);

    List<Admin> findAllByOrderByIdAsc();
}
