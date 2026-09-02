package com.quadmlabs.saphikuna.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.quadmlabs.saphikuna.entity.ProductCollection;
import com.quadmlabs.saphikuna.entity.ProductCollectionId;

public interface ProductCollectionRepository extends JpaRepository<ProductCollection, ProductCollectionId> {

}