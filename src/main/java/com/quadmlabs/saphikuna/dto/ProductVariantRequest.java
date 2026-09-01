package com.quadmlabs.saphikuna.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ProductVariantRequest {

    private UUID colorId;

    private List<UUID> sizeIds;

    private Integer stock;

    private boolean differentPrice;

    private BigDecimal price;

    public UUID getColorId() {
        return colorId;
    }

    public void setColorId(UUID colorId) {
        this.colorId = colorId;
    }

    public List<UUID> getSizeIds() {
        return sizeIds;
    }

    public void setSizeIds(List<UUID> sizeIds) {
        this.sizeIds = sizeIds;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public boolean isDifferentPrice() {
        return differentPrice;
    }

    public void setDifferentPrice(boolean differentPrice) {
        this.differentPrice = differentPrice;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}