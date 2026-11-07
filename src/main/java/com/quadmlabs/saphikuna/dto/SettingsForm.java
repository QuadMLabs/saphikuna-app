package com.quadmlabs.saphikuna.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Formulario de Configuración. */
@Getter
@Setter
public class SettingsForm {

    @NotNull(message = "{settings.validation.days.required}")
    @Min(value = 1, message = "{settings.validation.days.range}")
    @Max(value = 365, message = "{settings.validation.days.range}")
    private Integer newProductDays;
}
