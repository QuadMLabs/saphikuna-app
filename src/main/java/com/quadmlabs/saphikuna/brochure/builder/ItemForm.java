package com.quadmlabs.saphikuna.brochure.builder;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La ficha de una prenda dentro del catálogo: qué papel tiene cada foto (principal, apoyo o no usar)
 * y los dos textos.
 */
@Getter
@Setter
public class ItemForm {

    /** id de la foto -> "main" | "extra" | "none". */
    private Map<String, String> roles = new LinkedHashMap<>();

    @Size(max = BrochureBuilderService.MAX_HIGHLIGHT, message = "{brochures.validation.highlight.max}")
    private String highlightText;

    @Size(max = BrochureBuilderService.MAX_DETAILS, message = "{brochures.validation.details.max}")
    private String detailsText;
}
