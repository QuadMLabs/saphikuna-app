package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class ProductCollectionId implements Serializable {

    private UUID productId;
    private UUID collectionId;

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(UUID collectionId) {
        this.collectionId = collectionId;
    }
}