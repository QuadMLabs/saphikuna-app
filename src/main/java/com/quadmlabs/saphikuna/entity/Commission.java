package com.quadmlabs.saphikuna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Encargo: obra por pedido para una marca o empresa, con fecha de entrega. */
@Getter
@Setter
@Entity
@Table(name = "commissions")
public class Commission {

    public static final String TO_START = "Por empezar";
    public static final String IN_PROGRESS = "En proceso";
    public static final String READY = "Listo";
    public static final String DELIVERED = "Entregado";

    public static final List<String> STATUSES = List.of(TO_START, IN_PROGRESS, READY, DELIVERED);

    @Id
    @Column(name = "commissionId")
    private UUID commissionId;

    @Column(name = "clientName")
    private String clientName;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "dueDate")
    private LocalDate dueDate;

    @Column(name = "status")
    private String status = TO_START;

    @Column(name = "agreedPrice")
    private BigDecimal agreedPrice;

    @Column(name = "advancePaid")
    private BigDecimal advancePaid = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "createdAt")
    private OffsetDateTime createdAt;

    @Column(name = "updatedAt")
    private OffsetDateTime updatedAt;

    public boolean isDelivered() {
        return DELIVERED.equals(status);
    }

    /** Lo que falta cobrar; null si no hay precio acordado. */
    public BigDecimal getBalance() {
        if (agreedPrice == null) {
            return null;
        }
        return agreedPrice.subtract(advancePaid == null ? BigDecimal.ZERO : advancePaid);
    }
}
