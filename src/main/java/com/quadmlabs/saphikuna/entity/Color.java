package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "colors")
public class Color {

    @Id
    @Column(name = "colorId")
    private UUID colorId;

    @Column(name = "name")
    private String name;

    @Column(name = "hexCode")
    private String hexCode;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    public UUID getColorId() {
        return colorId;
    }

    public void setColorId(UUID colorId) {
        this.colorId = colorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHexCode() {
        return hexCode;
    }

    public void setHexCode(String hexCode) {
        this.hexCode = hexCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}