package com.quadmlabs.saphikuna.brochure;

import com.quadmlabs.saphikuna.dto.StoreInfoForm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Todo lo que necesita el catálogo (en línea y PDF) ya resuelto: sin cargas perezosas ni consultas. */
public record BrochureData(
        StoreInfoForm store,
        String title,
        /** Qué abarca: nombre de la categoría o colección, o vacío si es todo. */
        String scope,
        LocalDate generatedOn,
        List<Section> sections,
        int productCount,
        BrochureOptions options) {

    public boolean isEmpty() {
        return productCount == 0;
    }

    /** ¿Hay algún dato de contacto para mostrar? */
    public boolean hasContact() {
        return notBlank(store.getWhatsapp()) || notBlank(store.getPhone()) || notBlank(store.getEmail())
                || notBlank(store.getInstagram()) || notBlank(store.getFacebook()) || notBlank(store.getAddress());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Un grupo de productos (Novedades, una categoría...). {@code title} vacío = sin encabezado. */
    public record Section(String title, boolean highlight, List<Item> items) {

        /** Los productos en filas de {@code columns}; la última fila se completa con null. */
        public List<List<Item>> rows(int columns) {
            List<List<Item>> rows = new ArrayList<>();
            for (int i = 0; i < items.size(); i += columns) {
                List<Item> row = new ArrayList<>(items.subList(i, Math.min(i + columns, items.size())));
                while (row.size() < columns) {
                    row.add(null);
                }
                rows.add(row);
            }
            return rows;
        }
    }

    public record Item(
            UUID id,
            String code,
            String name,
            String description,
            String categoryName,
            /** Precio normal (sin descuento). */
            BigDecimal price,
            /** Precio final (con el descuento aplicado). */
            BigDecimal finalPrice,
            int discountPercent,
            boolean isNew,
            /** available, onRequest o soldOut. */
            String availability,
            List<String> sizes,
            List<ColorChip> colors,
            /** Tallas con un precio distinto al general. */
            List<SizePrice> specialPrices,
            /** Fotos (la principal primero). */
            List<String> photos,
            /** Enlace para consultar por WhatsApp; null si no hay número. */
            String whatsappUrl) {

        public boolean hasDiscount() {
            return discountPercent > 0;
        }

        public boolean isOnRequest() {
            return "onRequest".equals(availability);
        }

        public boolean isSoldOut() {
            return "soldOut".equals(availability);
        }

        /** Descripción corta (para el PDF): hasta ~{@code max} caracteres, sin cortar palabras. */
        public String summary(int max) {
            if (description == null || description.length() <= max) {
                return description;
            }
            int cut = description.lastIndexOf(' ', max);
            return description.substring(0, cut > max / 2 ? cut : max).trim() + "…";
        }
    }

    public record ColorChip(String name, String hex, boolean primary) {
    }

    public record SizePrice(String size, BigDecimal price) {
    }
}
