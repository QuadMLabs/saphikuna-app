package com.quadmlabs.saphikuna.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.quadmlabs.saphikuna.entity.Product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Datos del formulario de producto (crear y editar). Se enlaza con th:object,
 * por lo que no se necesita JavaScript para armar la petición.
 */
@Getter
@Setter
public class ProductForm {

    public static final int MAX_IMAGES = 4;

    @NotBlank(message = "{products.form.validation.name.required}")
    @Size(max = 150, message = "{products.form.validation.name.max}")
    private String name;

    /** Solo para mostrar: lo asigna el servidor y se ignora al guardar. */
    private String code;

    @Size(max = 20, message = "{products.form.validation.label.max}")
    private String label;

    @Size(max = 2000, message = "{products.form.validation.description.max}")
    private String description;

    @NotNull(message = "{products.form.validation.price.required}")
    @DecimalMin(value = "0.00", message = "{products.form.validation.price.min}")
    private BigDecimal generalPrice;

    @DecimalMin(value = "0", message = "{products.form.validation.discount.range}")
    @DecimalMax(value = "90", message = "{products.form.validation.discount.range}")
    private BigDecimal discountPercent;

    /** Visible en la tienda. */
    private boolean active = true;

    @NotBlank
    private String availability = Product.AVAILABLE;

    private UUID categoryId;

    private UUID collectionId;

    private List<UUID> primaryColorIds = new ArrayList<>();

    private List<UUID> secondaryColorIds = new ArrayList<>();

    /** Tallas disponibles. */
    private List<UUID> sizeIds = new ArrayList<>();

    /** Talla poco común con precio distinto (opcional): talla + precio. */
    private UUID specialSizeId;

    @DecimalMin(value = "0.00", message = "{products.form.validation.price.min}")
    private BigDecimal specialSizePrice;

    private List<@Valid SpecRow> specs = new ArrayList<>();

    /** Imágenes ya guardadas (solo edición). */
    private List<ImageRow> images = new ArrayList<>();

    /**
     * Foto principal: "existing:<id>" para una ya guardada o "new:<n>" para la
     * n-ésima foto nueva (desde 0). Si falta o no es válida, es la primera.
     */
    private String primary;

    /** Imágenes nuevas a subir. */
    private List<MultipartFile> newImages = new ArrayList<>();

    @Getter
    @Setter
    public static class SpecRow {

        @Size(max = 60, message = "{products.form.validation.spec.max}")
        private String name;

        @Size(max = 200, message = "{products.form.validation.spec.max}")
        private String value;

        public boolean isBlank() {
            return (name == null || name.isBlank()) && (value == null || value.isBlank());
        }
    }

    @Getter
    @Setter
    public static class ImageRow {

        private UUID id;

        private boolean delete;

        /** Solo para mostrar; nunca se usa al guardar. */
        private String thumbUrl;
    }
}
