package com.quadmlabs.saphikuna.dto;

import java.util.UUID;

/** Filtros del listado de productos (llegan por query string). */
public record ProductFilter(String q, UUID categoryId, UUID collectionId, String status, String availability) {
}
