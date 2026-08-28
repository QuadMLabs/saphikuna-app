package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sizes")
public class Size {

    @Id
    @Column(name = "sizeId")
    private UUID sizeId;

    @Column(name = "name")
    private String name;

    @Column(name = "sortOrder")
    private Short sortOrder;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    public UUID getSizeId() {
        return sizeId;
    }

    public void setSizeId(UUID sizeId) {
        this.sizeId = sizeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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