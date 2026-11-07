package com.quadmlabs.saphikuna.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Un catálogo administrable (colores, tallas, categorías, colecciones).
 * La pantalla de administración es la misma para todos; cada uno solo declara
 * sus campos y cómo se guarda.
 */
public interface CatalogAdapter {

    /** Parte de la URL y clave de mensajes: colors, sizes, categories, collections. */
    String key();

    List<CatalogField> fields();

    /** Todos los elementos (filtrados por {@code q} si no está vacío), con su uso. */
    List<CatalogItem> list(String q);

    Optional<CatalogItem> find(UUID id);

    /** Valores iniciales del formulario "Nuevo". */
    Map<String, String> defaults();

    /**
     * Valida y guarda ({@code id == null} crea). Devuelve los errores como
     * campo → clave de mensaje; vacío si se guardó.
     */
    Map<String, String> save(UUID id, Map<String, String> values);

    /** Elimina solo si ningún producto lo usa. */
    DeleteResult delete(UUID id);

    record DeleteResult(boolean deleted, String name, long usage) {
    }
}
