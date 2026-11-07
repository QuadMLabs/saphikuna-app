package com.quadmlabs.saphikuna.service;

import com.quadmlabs.saphikuna.dto.StoreInfoForm;

/** Ajustes de la aplicación que el usuario puede cambiar en Configuración. */
public interface SettingsService {

    int DEFAULT_NEW_PRODUCT_DAYS = 15;
    int MIN_NEW_PRODUCT_DAYS = 1;
    int MAX_NEW_PRODUCT_DAYS = 365;

    String DEFAULT_STORE_NAME = "Saphikuna";
    String DEFAULT_TAGLINE = "Tejiendo sueños, vistiendo raíces";

    /**
     * Días que un producto nuevo se muestra como "nuevo" por defecto. Si el ajuste no existe,
     * no es válido o no se puede leer, devuelve {@link #DEFAULT_NEW_PRODUCT_DAYS}.
     */
    int newProductDays();

    void saveNewProductDays(int days);

    /**
     * Datos de la tienda. Los que no se han guardado salen con su valor por defecto (nombre y
     * frase) o vacíos (contactos). Si no se pueden leer, devuelve los valores por defecto.
     */
    StoreInfoForm storeInfo();

    void saveStoreInfo(StoreInfoForm info);
}
