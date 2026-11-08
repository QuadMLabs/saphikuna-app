package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Brochure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrochureRepository extends JpaRepository<Brochure, UUID> {

    List<Brochure> findAllByOrderByUpdatedAtDesc();
}
