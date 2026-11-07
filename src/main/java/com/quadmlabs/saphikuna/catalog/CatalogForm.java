package com.quadmlabs.saphikuna.catalog;

import java.util.LinkedHashMap;
import java.util.Map;

/** Valores enviados por el formulario: campos "values[nombre]". */
public class CatalogForm {

    private Map<String, String> values = new LinkedHashMap<>();

    public Map<String, String> getValues() {
        return values;
    }

    public void setValues(Map<String, String> values) {
        this.values = values;
    }
}
