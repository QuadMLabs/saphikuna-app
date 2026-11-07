package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "productSizes")
public class ProductSize {

    @Id
    @Column(name = "productSizeId")
    private UUID productSizeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sizeId")
    private Size size;

    /** Precio especial de esta talla; null = precio general del producto. */
    @Column(name = "price")
    private BigDecimal price;
}
