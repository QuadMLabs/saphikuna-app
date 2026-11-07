package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "productSpecifications")
public class ProductSpecification {

    @Id
    @Column(name = "productSpecificationId")
    private UUID productSpecificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId")
    private Product product;

    @Column(name = "name")
    private String name;

    @Column(name = "value")
    private String value;

    @Column(name = "sortOrder")
    private Short sortOrder;
}
