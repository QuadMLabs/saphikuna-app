package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    public static final String ACTIVE = "Activo";
    public static final String INACTIVE = "Inactivo";

    public static final String AVAILABLE = "Disponible";
    public static final String SOLD_OUT = "Agotado";
    public static final String ON_REQUEST = "Bajo pedido";

    @Id
    @Column(name = "producttId")
    private UUID productId;

    /** Código visible (ej. SPK-001). */
    @Column(name = "code")
    private String code;

    /** Etiqueta opcional para la tienda (ej. NEW, OFERTA). */
    @Column(name = "label")
    private String label;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    /** Activo / Inactivo: si se muestra o no en la tienda. */
    @Column(name = "status")
    private String status;

    /** Disponible / Agotado / Bajo pedido. */
    @Column(name = "availability")
    private String availability = AVAILABLE;

    /** Precio normal (sin descuento). */
    @Column(name = "generalPrice")
    private BigDecimal generalPrice;

    @Column(name = "discountPercent")
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "primaryImagePath")
    private String primaryImagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoryId")
    private Category category;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductCollection> productCollections = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductColor> productColors = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductSize> productSizes = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductSpecification> productSpecifications = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> productImages = new ArrayList<>();

    public boolean hasDiscount() {
        return discountPercent != null && discountPercent.signum() > 0;
    }

    /** Precio con el descuento aplicado (o el normal si no hay descuento). */
    public BigDecimal getFinalPrice() {
        if (generalPrice == null) {
            return null;
        }
        if (!hasDiscount()) {
            return generalPrice;
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return generalPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
