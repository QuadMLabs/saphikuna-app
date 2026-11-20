package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollaborationRepository extends JpaRepository<Collaboration, UUID> {

    /** Las más recientes primero. */
    List<Collaboration> findAllByOrderByCreatedAtDesc();

    /** Por nombre, para elegir la marca de un encargo. */
    List<Collaboration> findAllByOrderByBrandNameAsc();
}
