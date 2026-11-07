package com.quadmlabs.saphikuna.dto;

import com.quadmlabs.saphikuna.entity.Commission;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos del formulario de encargo (crear y editar). */
@Getter
@Setter
public class CommissionForm {

    @NotBlank(message = "{commissions.validation.client.required}")
    @Size(max = 120, message = "{commissions.validation.client.max}")
    private String clientName;

    @NotBlank(message = "{commissions.validation.title.required}")
    @Size(max = 150, message = "{commissions.validation.title.max}")
    private String title;

    @Size(max = 2000, message = "{commissions.validation.text.max}")
    private String description;

    @NotNull(message = "{commissions.validation.quantity.required}")
    @Min(value = 1, message = "{commissions.validation.quantity.range}")
    @Max(value = 100000, message = "{commissions.validation.quantity.range}")
    private Integer quantity = 1;

    @NotNull(message = "{commissions.validation.due.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @NotBlank
    private String status = Commission.TO_START;

    /** Opcional. */
    @DecimalMin(value = "0.00", message = "{commissions.validation.money.min}")
    private BigDecimal agreedPrice;

    @NotNull(message = "{commissions.validation.money.required}")
    @DecimalMin(value = "0.00", message = "{commissions.validation.money.min}")
    private BigDecimal advancePaid = BigDecimal.ZERO;

    @Size(max = 2000, message = "{commissions.validation.text.max}")
    private String notes;
}
