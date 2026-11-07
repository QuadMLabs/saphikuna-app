package com.quadmlabs.saphikuna.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quadmlabs.saphikuna.entity.Size;

public interface SizeRepository extends JpaRepository<Size, UUID> {
}