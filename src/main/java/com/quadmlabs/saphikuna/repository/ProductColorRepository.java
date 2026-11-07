package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.ProductColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductColorRepository extends JpaRepository<ProductColor, UUID> {

    /** [colorId, cantidad de productos] por cada color en uso. */
    @Query("select pc.color.colorId, count(pc) from ProductColor pc group by pc.color.colorId")
    List<Object[]> countByColor();
}
