package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsByCodeIgnoreCase(String code);

    /** [categoryId, cantidad de productos] por cada categoría en uso. */
    @Query("select p.category.categoryId, count(p) from Product p where p.category is not null group by p.category.categoryId")
    java.util.List<Object[]> countByCategory();

    /** Siguiente número de la secuencia de códigos (db/003_product_code_sequence.sql). */
    @Query(value = "select nextval('\"productCodeSeq\"')", nativeQuery = true)
    long nextCodeNumber();
}
