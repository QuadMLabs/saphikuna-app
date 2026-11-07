package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Persona que puede entrar al panel. La clave se guarda siempre con BCrypt. */
@Getter
@Setter
@Entity
@Table(name = "admins")
public class Admin {

    @Id
    @Column(name = "adminId")
    private UUID adminId;

    /** Siempre en minúsculas. */
    @Column(name = "username")
    private String username;

    @Column(name = "displayName")
    private String displayName;

    @Column(name = "passwordHash")
    private String passwordHash;

    @Column(name = "enabled")
    private boolean enabled = true;

    /** Intentos fallidos seguidos; vuelve a 0 al entrar bien o al bloquear. */
    @Column(name = "failedAttempts")
    private int failedAttempts;

    /** Si está en el futuro, la cuenta está bloqueada temporalmente. */
    @Column(name = "lockedUntil")
    private OffsetDateTime lockedUntil;

    @Column(name = "lastLoginAt")
    private OffsetDateTime lastLoginAt;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    public boolean isLockedAt(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
