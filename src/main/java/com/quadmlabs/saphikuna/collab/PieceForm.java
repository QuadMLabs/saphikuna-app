package com.quadmlabs.saphikuna.collab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Datos de una prenda de colaboración (crear y editar): nombre, descripción, colores y fotos. */
@Getter
@Setter
public class PieceForm {

    /** Máximo de fotos por prenda (después se suma el video corto). */
    public static final int MAX_IMAGES = 6;

    @NotBlank(message = "{collab.piece.validation.name.required}")
    @Size(max = 150, message = "{collab.piece.validation.name.max}")
    private String name;

    @Size(max = 1000, message = "{collab.piece.validation.description.max}")
    private String description;

    private List<UUID> colorIds = new ArrayList<>();

    /** Fotos ya guardadas (solo edición). */
    private List<ImageRow> images = new ArrayList<>();

    /** Foto principal: "existing:<id>" o "new:<n>" (n-ésima foto nueva, desde 0). Si falta, la primera. */
    private String primary;

    /** Fotos nuevas a subir. */
    private List<MultipartFile> newImages = new ArrayList<>();

    /** Video corto nuevo (MP4); vacío = no cambiar. Reemplaza al anterior si ya había uno. */
    private MultipartFile newVideo;

    /** Quitar el video que ya tiene (se ignora si se sube uno nuevo). */
    private boolean removeVideo;

    /** El audio del video es útil (en la tienda se podrá activar el sonido); si no, el video va mudo. */
    private boolean videoHasSound;

    /** Solo para mostrar el video actual; nunca se usa al guardar. */
    private String videoUrl;

    @Getter
    @Setter
    public static class ImageRow {

        private UUID id;

        private boolean delete;

        /** Solo para mostrar; nunca se usa al guardar. */
        private String thumbUrl;
    }
}
