package com.quadmlabs.saphikuna.brochure.builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Datos del catálogo: nombre, texto de inicio y si lleva precios. */
@Getter
@Setter
public class BrochureForm {

    @NotBlank(message = "{brochures.validation.name.required}")
    @Size(max = BrochureBuilderService.MAX_NAME, message = "{brochures.validation.name.max}")
    private String name;

    @Size(max = BrochureBuilderService.MAX_INTRO, message = "{brochures.validation.intro.max}")
    private String introText;

    private boolean showPrices = true;
}
