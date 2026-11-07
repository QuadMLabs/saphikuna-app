package com.quadmlabs.saphikuna.brochure;

import lombok.Getter;
import lombok.Setter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opciones del catálogo. Llegan por la URL (por eso el enlace público se puede compartir) y
 * todas son opcionales: lo que no se indique usa su valor por defecto.
 */
@Getter
@Setter
public class BrochureOptions {

    public static final String DEFAULT_TITLE = "Catálogo";
    public static final int MAX_TITLE = 60;
    public static final int MAX_PHOTOS = 4;

    /** Título de la portada. */
    private String title = DEFAULT_TITLE;

    /** Solo una categoría (id). Vacío = todas. */
    private String category;

    /** Solo una colección (id). Vacío = todas. */
    private String collection;

    /** Solo los productos que hoy se muestran como nuevos. */
    private boolean news = false;

    private boolean prices = true;
    private boolean sizes = true;
    private boolean colors = true;
    private boolean desc = true;

    /** Incluir también los agotados. */
    private boolean soldout = false;

    /** Agrupar por secciones (Novedades y una por categoría). */
    private boolean group = true;

    /** Fotos por producto (1 a 4). */
    private int photos = 1;

    // ------------------------------------------------------------------

    public String titleOrDefault() {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE).trim() : t;
    }

    public int photoCount() {
        return Math.max(1, Math.min(MAX_PHOTOS, photos));
    }

    public UUID categoryId() {
        return parse(category);
    }

    public UUID collectionId() {
        return parse(collection);
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null; // un id inválido en la URL se ignora
        }
    }

    /**
     * Query string con SOLO lo que difiere de los valores por defecto (vacía si todo es por
     * defecto): "?news=true&photos=2". Sirve para armar los enlaces al catálogo.
     */
    public String query() {
        List<String> parts = new ArrayList<>();

        String t = titleOrDefault();
        if (!DEFAULT_TITLE.equals(t)) {
            parts.add("title=" + encode(t));
        }
        if (categoryId() != null) {
            parts.add("category=" + categoryId());
        }
        if (collectionId() != null) {
            parts.add("collection=" + collectionId());
        }
        if (news) {
            parts.add("news=true");
        }
        if (!prices) {
            parts.add("prices=false");
        }
        if (!sizes) {
            parts.add("sizes=false");
        }
        if (!colors) {
            parts.add("colors=false");
        }
        if (!desc) {
            parts.add("desc=false");
        }
        if (soldout) {
            parts.add("soldout=true");
        }
        if (!group) {
            parts.add("group=false");
        }
        if (photoCount() != 1) {
            parts.add("photos=" + photoCount());
        }
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
