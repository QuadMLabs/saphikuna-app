package com.quadmlabs.saphikuna.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ProductRequest {

    private UUID productId;
    private String name;
    private BigDecimal generalPrice;
    private String description;
    private UUID categoryId;
    private String status;
    private List<ProductVariantRequest> variants;
    private List<UUID> collectionIds;
    private List<UUID> existingImageIds;
    private UUID primaryImageId;
    private Integer primaryNewImageIndex;

    /**
     * GETTERS & SETTERS
     */

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public List<UUID> getExistingImageIds() {
        return existingImageIds;
    }

    public void setExistingImageIds(List<UUID> existingImageIds) {
        this.existingImageIds = existingImageIds;
    }

    public UUID getPrimaryImageId() {
        return primaryImageId;
    }

    public void setPrimaryImageId(UUID primaryImageId) {
        this.primaryImageId = primaryImageId;
    }

    public Integer getPrimaryNewImageIndex() {
        return primaryNewImageIndex;
    }

    public void setPrimaryNewImageIndex(Integer primaryNewImageIndex) {
        this.primaryNewImageIndex = primaryNewImageIndex;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ProductVariantRequest> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariantRequest> variants) {
        this.variants = variants;
    }

    public List<UUID> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<UUID> collectionIds) {
        this.collectionIds = collectionIds;
    }
}
