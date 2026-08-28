package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}