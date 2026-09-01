package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "productImages")
public class ProductImage {

    @Id
    @Column(name = "productImagesId")
    private UUID productImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "productId",
            nullable = false
    )
    private Product product;

    @Column(name = "storagePath")
    private String storagePath;

    @Column(name = "sortOrder")
    private Short sortOrder;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    public UUID getProductImageId() {
        return productImageId;
    }

    public void setProductImageId(UUID productImageId) {
        this.productImageId = productImageId;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Short getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Short sortOrder) {
        this.sortOrder = sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}