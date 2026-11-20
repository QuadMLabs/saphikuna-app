package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Una prenda hecha para una marca: solo se muestra (sin precio, tallas ni disponibilidad). */
@Getter
@Setter
@Entity
@Table(name = "collabPieces")
public class CollabPiece {

    @Id
    @Column(name = "collabPieceId")
    private UUID collabPieceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collaborationId", nullable = false)
    private Collaboration collaboration;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    /** Encargo del que viene la prenda (null = se agregó a mano); evita crearla dos veces. */
    @Column(name = "commissionId")
    private UUID commissionId;

    @Column(name = "primaryImagePath")
    private String primaryImagePath;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;

    @ManyToMany
    @JoinTable(name = "collabPieceColors",
            joinColumns = @JoinColumn(name = "collabPieceId"),
            inverseJoinColumns = @JoinColumn(name = "colorId"))
    private Set<Color> colors = new LinkedHashSet<>();

    @OneToMany(mappedBy = "piece", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CollabPieceImage> images = new ArrayList<>();
}
