package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Una prenda dentro de un catálogo, con las fotos y los dos textos elegidos para ese catálogo. */
@Getter
@Setter
@Entity
@Table(name = "brochureItems")
public class BrochureItem {

    @Id
    @Column(name = "brochureItemId")
    private UUID brochureItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brochureId")
    private Brochure brochure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId")
    private Product product;

    @Column(name = "position")
    private int position;

    /** Foto principal (grande). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mainImageId")
    private ProductImage mainImage;

    /** Fotos de apoyo (máximo 2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraImage1Id")
    private ProductImage extraImage1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraImage2Id")
    private ProductImage extraImage2;

    /** Frase destacada de la prenda (por ejemplo, su descripción). */
    @Column(name = "highlightText")
    private String highlightText;

    /** Ficha: material, colores, tallas y lo que se quiera aclarar. */
    @Column(name = "detailsText")
    private String detailsText;

    /** Las fotos de apoyo que hay, sin huecos. */
    public List<ProductImage> extras() {
        List<ProductImage> list = new ArrayList<>(2);
        if (extraImage1 != null) {
            list.add(extraImage1);
        }
        if (extraImage2 != null) {
            list.add(extraImage2);
        }
        return list;
    }
}
