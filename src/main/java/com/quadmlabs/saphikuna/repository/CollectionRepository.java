package com.quadmlabs.saphikuna.repository;

import java.util.UUID;

import com.quadmlabs.saphikuna.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {
}