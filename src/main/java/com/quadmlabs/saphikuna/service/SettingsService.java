package com.quadmlabs.saphikuna.service;

/** Ajustes de la aplicación que el usuario puede cambiar en Configuración. */
public interface SettingsService {

    int DEFAULT_NEW_PRODUCT_DAYS = 15;
    int MIN_NEW_PRODUCT_DAYS = 1;
    int MAX_NEW_PRODUCT_DAYS = 365;

    /**
     * Días que un producto nuevo se muestra como "nuevo" por defecto. Si el ajuste no existe,
     * no es válido o no se puede leer, devuelve {@link #DEFAULT_NEW_PRODUCT_DAYS}.
     */
    int newProductDays();

    void saveNewProductDays(int days);
}
