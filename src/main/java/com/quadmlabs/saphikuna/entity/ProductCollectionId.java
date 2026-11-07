package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductCollectionId implements Serializable {

    @Column(name = "productId")
    private UUID productId;

    @Column(name = "collectionId")
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

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (!(o instanceof ProductCollectionId)) {
            return false;
        }

        ProductCollectionId that = (ProductCollectionId) o;

        return Objects.equals(productId, that.productId)
                && Objects.equals(collectionId, that.collectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, collectionId);
    }
}