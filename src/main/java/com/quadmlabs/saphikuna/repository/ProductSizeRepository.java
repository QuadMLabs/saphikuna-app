package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.ProductSize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductSizeRepository extends JpaRepository<ProductSize, UUID> {

    /** [sizeId, cantidad de productos] por cada talla en uso. */
    @Query("select ps.size.sizeId, count(ps) from ProductSize ps group by ps.size.sizeId")
    List<Object[]> countBySize();
}
