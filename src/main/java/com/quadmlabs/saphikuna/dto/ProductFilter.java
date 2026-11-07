package com.quadmlabs.saphikuna.dto;

import java.util.UUID;

/**
 * Filtros del listado de productos (llegan por query string).
 * {@code missing}: lo que le falta al producto (photo, description, category, colors, sizes).
 * {@code onlyNew}: solo los que hoy se muestran como nuevos.
 */
public record ProductFilter(String q, UUID categoryId, UUID collectionId, String status, String availability,
        String missing, boolean onlyNew) {

    /** Valores válidos de {@code missing}. */
    public static final java.util.List<String> MISSING = java.util.List.of("photo", "description", "category", "colors", "sizes");
}
