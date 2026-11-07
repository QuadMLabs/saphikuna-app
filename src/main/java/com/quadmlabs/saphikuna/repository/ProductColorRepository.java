package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.ProductColor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductColorRepository extends JpaRepository<ProductColor, UUID> {

    boolean existsByColor(Color color);
}
