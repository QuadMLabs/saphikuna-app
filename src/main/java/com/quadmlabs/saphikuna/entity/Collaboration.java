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

/** Una marca para la que se hicieron prendas por encargo (solo se muestra el nombre). */
@Getter
@Setter
@Entity
@Table(name = "collaborations")
public class Collaboration {

    @Id
    @Column(name = "collaborationId")
    private UUID collaborationId;

    @Column(name = "brandName")
    private String brandName;

    /** Si se muestra o no en la tienda. */
    @Column(name = "visible")
    private boolean visible = true;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "collaboration", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<CollabPiece> pieces = new ArrayList<>();
}
