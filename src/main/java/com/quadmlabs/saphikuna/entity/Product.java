package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "producttId")
    private UUID productId;

    @Column(name = "primaryImagePath")
    private String primaryImagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoryId")
    private Category category;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductCollection> productCollections = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductVariant> productVariants = new ArrayList<>();

    public List<ProductCollection> getProductCollections() {
        return productCollections;
    }

    public void setProductCollections(List<ProductCollection> productCollections) {
        this.productCollections = productCollections;
    }

    public List<ProductVariant> getProductVariants() {
        return productVariants;
    }

    public void setProductVariants(List<ProductVariant> productVariants) {
        this.productVariants = productVariants;
    }

    public List<Color> getColors() {
        return productVariants.stream()
                .map(ProductVariant::getColor)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<String> getSizes() {
        return productVariants.stream()
                .map(ProductVariant::getSize)
                .filter(Objects::nonNull)
                .map(Size::getName)
                .distinct()
                .toList();
    }

    public String getPriceDisplay() {
        List<BigDecimal> prices = productVariants.stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (prices.isEmpty()) {
            return "—";
        }

        if (prices.size() == 1) {
            return "$" + prices.get(0).setScale(2).toPlainString();
        }

        return "$" + prices.get(0).setScale(2).toPlainString()
                + " – $"
                + prices.get(prices.size() - 1).setScale(2).toPlainString();
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getPrimaryImagePath() {
        return primaryImagePath;
    }

    public void setPrimaryImagePath(String primaryImagePath) {
        this.primaryImagePath = primaryImagePath;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}