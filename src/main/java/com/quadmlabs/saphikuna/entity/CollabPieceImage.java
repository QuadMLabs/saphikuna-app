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

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "collabPieceImages")
public class CollabPieceImage {

    @Id
    @Column(name = "collabPieceImageId")
    private UUID collabPieceImageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collabPieceId", nullable = false)
    private CollabPiece piece;

    @Column(name = "storagePath")
    private String storagePath;

    @Column(name = "sortOrder")
    private Short sortOrder;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;
}
