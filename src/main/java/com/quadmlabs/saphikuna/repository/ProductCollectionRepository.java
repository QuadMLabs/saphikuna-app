package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.ProductCollection;
import com.quadmlabs.saphikuna.entity.ProductCollectionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductCollectionRepository extends JpaRepository<ProductCollection, ProductCollectionId> {

    /** [collectionId, cantidad de productos] por cada colección en uso. */
    @Query("select pc.collection.collectionId, count(pc) from ProductCollection pc group by pc.collection.collectionId")
    List<Object[]> countByCollection();
}
