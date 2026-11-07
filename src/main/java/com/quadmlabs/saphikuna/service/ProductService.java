package com.quadmlabs.saphikuna.service;

import com.quadmlabs.saphikuna.dto.ProductFilter;
import com.quadmlabs.saphikuna.dto.ProductForm;
import com.quadmlabs.saphikuna.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    Page<Product> search(ProductFilter filter, Pageable pageable);

    Product findById(UUID productId);

    /** Formulario vacío para un producto nuevo (con código sugerido). */
    ProductForm newForm();

    /** Formulario precargado para editar. */
    ProductForm toForm(UUID productId);

    /** Completa en un formulario recibido los datos que solo sirven para mostrar. */
    void decorate(ProductForm form, UUID productId);

    /** Crea un producto y devuelve su id. */
    UUID create(ProductForm form);

    /**
     * Crea un producto con un código ya definido (por ejemplo, al importar los
     * productos de la tienda). No consume números de la secuencia.
     */
    UUID create(ProductForm form, String code);

    void update(UUID productId, ProductForm form);

    /** Borra el producto con sus datos relacionados y sus imágenes del storage. */
    void delete(UUID productId);
}
