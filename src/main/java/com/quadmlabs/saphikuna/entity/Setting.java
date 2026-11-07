package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Un ajuste de la aplicación (clave/valor). */
@Getter
@Setter
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "value")
    private String value;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;
}
