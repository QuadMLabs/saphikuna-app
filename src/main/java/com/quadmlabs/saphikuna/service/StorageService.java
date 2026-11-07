package com.quadmlabs.saphikuna.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Sube la imagen optimizada (WebP pequeño) en {@code path} y una miniatura
     * en {@link #thumbPath(String)}.
     */
    String upload(MultipartFile file, String path);

    /** ¿Responde Supabase Storage con la clave configurada? No lanza excepciones (máx. ~3 s). */
    boolean ping();

    String getPublicUrl(String path);

    /** Borra la imagen y su miniatura. */
    void delete(String path);

    /** Ruta de la miniatura asociada a una imagen. */
    default String thumbPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        return path.endsWith(".webp")
                ? path.substring(0, path.length() - 5) + "-thumb.webp"
                : path + "-thumb";
    }

    default String getThumbPublicUrl(String path) {
        return getPublicUrl(thumbPath(path));
    }
}
