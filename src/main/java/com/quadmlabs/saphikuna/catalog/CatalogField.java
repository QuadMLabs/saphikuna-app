package com.quadmlabs.saphikuna.catalog;

/** Campo editable de un catálogo (lo usa el formulario genérico). */
public record CatalogField(String name, String labelKey, Type type, boolean required, int maxLength) {

    public enum Type {
        TEXT, TEXTAREA, COLOR, NUMBER
    }
}
