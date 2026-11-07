package com.quadmlabs.saphikuna.catalog;

import java.util.Map;
import java.util.UUID;

/** Una fila de un catálogo: id, nombre, valores de sus campos y cuántos productos la usan. */
public record CatalogItem(UUID id, String name, Map<String, String> values, long usage) {
}
