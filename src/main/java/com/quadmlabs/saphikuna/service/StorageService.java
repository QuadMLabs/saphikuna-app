package com.quadmlabs.saphikuna.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Sube la imagen optimizada (WebP pequeño) en {@code path} y una miniatura
     * en {@link #thumbPath(String)}.
     */
    String upload(MultipartFile file, String path);

    /**
     * Sube un video corto tal cual (no se recodifica). Solo MP4 y hasta {@link #MAX_VIDEO_BYTES};
     * si no cumple, lanza {@link IllegalArgumentException} con un mensaje que se puede mostrar.
     */
    String uploadVideo(MultipartFile file, String path);

    /** Tamaño máximo de un video (el mismo límite de subida que tienen las fotos). */
    long MAX_VIDEO_BYTES = 15L * 1024 * 1024;

    /** Revisa que sea un MP4 de tamaño permitido; lanza un mensaje claro si no. */
    static void checkVideo(String contentType, String name, long size, byte[] head) {
        if (size <= 0) {
            throw new IllegalArgumentException("El video está vacío");
        }
        if (size > MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException("El video pesa " + (size / 1024 / 1024)
                    + " MB y el máximo es " + (MAX_VIDEO_BYTES / 1024 / 1024) + " MB");
        }
        boolean mp4Name = name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
        boolean mp4Type = "video/mp4".equalsIgnoreCase(contentType);
        // Un MP4 real trae "ftyp" en los bytes 4 a 7: así no basta con cambiarle el nombre a otro archivo
        boolean mp4Head = head != null && head.length >= 8 && head[4] == 'f' && head[5] == 't'
                && head[6] == 'y' && head[7] == 'p';
        if (!(mp4Name || mp4Type) || !mp4Head) {
            throw new IllegalArgumentException("El video debe ser un archivo MP4");
        }
    }

    /** Revisa un video antes de guardar nada (para avisar bien en el formulario); no sube nada. */
    default void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        byte[] head = new byte[8];
        int read;
        try (java.io.InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("No se pudo leer el video");
        }
        checkVideo(file.getContentType(), file.getOriginalFilename(), file.getSize(),
                java.util.Arrays.copyOf(head, read));
    }

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
