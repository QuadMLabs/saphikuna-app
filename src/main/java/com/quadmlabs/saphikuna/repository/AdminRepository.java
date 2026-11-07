package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByEnabledTrue();

    List<Admin> findAllByOrderByUsernameAsc();
}
