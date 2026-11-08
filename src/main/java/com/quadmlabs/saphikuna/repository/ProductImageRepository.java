package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.ProductImage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
}
