package com.quadmlabs.saphikuna.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quadmlabs.saphikuna.entity.Color;

public interface ColorRepository extends JpaRepository<Color, UUID> {
}