package com.quadmlabs.saphikuna.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ProductRequest {

    private String name;

    private BigDecimal generalPrice;

    private String description;

    private UUID categoryId;

    private String status;

    private List<ProductVariantRequest> variants;

    private List<UUID> collectionIds;

    public List<UUID> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<UUID> collectionIds) {
        this.collectionIds = collectionIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getGeneralPrice() {
        return generalPrice;
    }

    public void setGeneralPrice(BigDecimal generalPrice) {
        this.generalPrice = generalPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public List<ProductVariantRequest> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariantRequest> variants) {
        this.variants = variants;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
