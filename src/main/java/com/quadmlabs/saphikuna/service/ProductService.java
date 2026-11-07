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

    /** Cifras para el Home. */
    ProductStats stats();

    /** Cuántos productos tienen pendiente "photo", "description", "category", "colors" o "sizes". */
    long countMissing(String kind);

    /** Los últimos productos creados o editados. */
    java.util.List<Product> recent(int limit);

    record ProductStats(long total, long visible, long hidden, long available, long onRequest, long soldOut,
            long discounted, long news) {
    }

    /** Formulario vacío para un producto nuevo (con código sugerido). */
    ProductForm newForm();

    /** Formulario precargado para editar. */
    ProductForm toForm(UUID productId);

    /** Completa en un formulario recibido los datos que solo sirven para mostrar. */
    void decorate(ProductForm form, UUID productId);

    /** Crea un producto y devuelve su id. */
    UUID create(ProductForm form);

    void update(UUID productId, ProductForm form);

    /** Borra el producto con sus datos relacionados y sus imágenes del storage. */
    void delete(UUID productId);
}
