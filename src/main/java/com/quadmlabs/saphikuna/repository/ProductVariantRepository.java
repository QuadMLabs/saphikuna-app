package com.quadmlabs.saphikuna.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quadmlabs.saphikuna.entity.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID>{    
}