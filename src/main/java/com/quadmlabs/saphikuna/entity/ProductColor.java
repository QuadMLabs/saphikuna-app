package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "productColors")
public class ProductColor {

    @Id
    @Column(name = "productColorId")
    private UUID productColorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colorId")
    private Color color;

    /** true = color principal; false = color secundario. */
    @Column(name = "isPrimary")
    private boolean primary = true;
}
