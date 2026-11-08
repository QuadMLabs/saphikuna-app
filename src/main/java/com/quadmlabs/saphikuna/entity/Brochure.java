package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un catálogo armado desde el panel: nombre, texto de inicio y las prendas elegidas. */
@Getter
@Setter
@Entity
@Table(name = "brochures")
public class Brochure {

    @Id
    @Column(name = "brochureId")
    private UUID brochureId;

    @Column(name = "name")
    private String name;

    /** Texto de inicio (la frase de la página que abre el catálogo). */
    @Column(name = "introText")
    private String introText;

    @Column(name = "showPrices")
    private boolean showPrices = true;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "brochure", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BrochureItem> items = new ArrayList<>();
}
