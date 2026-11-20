package com.quadmlabs.saphikuna.collab;

import com.quadmlabs.saphikuna.entity.CollabPiece;
import com.quadmlabs.saphikuna.entity.CollabPieceImage;
import com.quadmlabs.saphikuna.entity.Collaboration;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.repository.CollabPieceRepository;
import com.quadmlabs.saphikuna.repository.CollaborationRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.CommissionRepository;
import com.quadmlabs.saphikuna.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Colaboraciones: marcas para las que se hicieron prendas por encargo. Se muestran en la tienda
 * pero no son parte del catálogo (sin precio, tallas ni disponibilidad).
 */
@Service
public class CollaborationService {

    private static final Logger log = LoggerFactory.getLogger(CollaborationService.class);

    private final CollaborationRepository brands;
    private final CollabPieceRepository pieces;
    private final ColorRepository colors;
    private final CommissionRepository commissions;
    private final StorageService storage;
    private final Clock clock;

    public CollaborationService(CollaborationRepository brands, CollabPieceRepository pieces,
            ColorRepository colors, CommissionRepository commissions, StorageService storage, Clock clock) {
        this.brands = brands;
        this.pieces = pieces;
        this.colors = colors;
        this.commissions = commissions;
        this.storage = storage;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Lo que muestra la lista
    // ------------------------------------------------------------------

    public record ColorDot(String name, String hex) {
    }

    public record PieceRow(UUID id, String name, String thumbUrl, List<ColorDot> colors, int photoCount) {
    }

    public record BrandRow(UUID id, String name, boolean visible, List<PieceRow> pieces) {
    }

    /** Todas las marcas con sus prendas, la más reciente primero. */
    @Transactional(readOnly = true)
    public List<BrandRow> brands() {
        List<BrandRow> rows = new ArrayList<>();
        for (Collaboration c : brands.findAllByOrderByCreatedAtDesc()) {
            List<PieceRow> pieceRows = new ArrayList<>();
            for (CollabPiece p : c.getPieces()) {
                List<ColorDot> dots = p.getColors().stream()
                        .map(col -> new ColorDot(col.getName(), col.getHexCode())).toList();
                pieceRows.add(new PieceRow(p.getCollabPieceId(), p.getName(), thumb(p.getPrimaryImagePath()),
                        dots, p.getImages().size()));
            }
            rows.add(new BrandRow(c.getCollaborationId(), c.getBrandName(), c.isVisible(), pieceRows));
        }
        return rows;
    }

    private String thumb(String path) {
        return path == null || path.isBlank() ? null : storage.getThumbPublicUrl(path);
    }

    // ------------------------------------------------------------------
    // Marcas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public String brandName(UUID brandId) {
        return findBrand(brandId).getBrandName();
    }

    @Transactional(readOnly = true)
    public BrandForm brandForm(UUID brandId) {
        Collaboration c = findBrand(brandId);
        BrandForm form = new BrandForm();
        form.setBrandName(c.getBrandName());
        form.setVisible(c.isVisible());
        return form;
    }

    @Transactional
    public UUID createBrand(BrandForm form) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Collaboration c = new Collaboration();
        c.setCollaborationId(UUID.randomUUID());
        c.setBrandName(form.getBrandName().trim());
        c.setVisible(form.isVisible());
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return brands.save(c).getCollaborationId();
    }

    @Transactional
    public void updateBrand(UUID brandId, BrandForm form) {
        Collaboration c = findBrand(brandId);
        c.setBrandName(form.getBrandName().trim());
        c.setVisible(form.isVisible());
        c.setUpdatedAt(OffsetDateTime.now(clock));
        brands.save(c);
        // Los encargos de esta marca muestran el nombre nuevo
        commissions.renameClient(brandId, c.getBrandName());
    }

    /** Borra la marca con todas sus prendas y sus fotos. */
    @Transactional
    public void deleteBrand(UUID brandId) {
        Collaboration c = findBrand(brandId);
        List<String> paths = new ArrayList<>();
        c.getPieces().forEach(p -> p.getImages().forEach(i -> paths.add(i.getStoragePath())));
        brands.delete(c);
        deleteAfterCommit(paths);
    }

    private Collaboration findBrand(UUID id) {
        return brands.findById(id).orElseThrow(() -> new IllegalArgumentException("Marca no encontrada"));
    }

    /**
     * Un encargo de la marca quedó entregado: se agrega como prenda (sin fotos, se suben después).
     * Si ya se había agregado antes, no hace nada.
     */
    @Transactional
    public void addDeliveredPiece(UUID brandId, UUID commissionId, String title, String description) {
        if (pieces.existsByCommissionId(commissionId)) {
            return;
        }
        Collaboration brand = findBrand(brandId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        CollabPiece p = new CollabPiece();
        p.setCollabPieceId(UUID.randomUUID());
        p.setCollaboration(brand);
        p.setCommissionId(commissionId);
        p.setName(title.trim());
        String text = blankToNull(description);
        p.setDescription(text == null || text.length() <= 1000 ? text : text.substring(0, 1000));
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        brand.getPieces().add(p);
    }

    // ------------------------------------------------------------------
    // Prendas: formularios
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UUID brandOfPiece(UUID pieceId) {
        return findPiece(pieceId).getCollaboration().getCollaborationId();
    }

    @Transactional(readOnly = true)
    public PieceForm toPieceForm(UUID pieceId) {
        CollabPiece p = findPiece(pieceId);
        PieceForm form = new PieceForm();
        form.setName(p.getName());
        form.setDescription(p.getDescription());
        p.getColors().forEach(c -> form.getColorIds().add(c.getColorId()));
        for (CollabPieceImage img : p.getImages()) {
            PieceForm.ImageRow row = new PieceForm.ImageRow();
            row.setId(img.getCollabPieceImageId());
            row.setThumbUrl(storage.getThumbPublicUrl(img.getStoragePath()));
            form.getImages().add(row);
            if (img.getStoragePath().equals(p.getPrimaryImagePath())) {
                form.setPrimary("existing:" + img.getCollabPieceImageId());
            }
        }
        return form;
    }

    /** Tras un error de validación: el POST solo trae id y "quitar" de cada foto; se repone la miniatura. */
    @Transactional(readOnly = true)
    public void decorate(PieceForm form, UUID pieceId) {
        if (pieceId == null) {
            form.getImages().clear();
            return;
        }
        CollabPiece p = findPiece(pieceId);
        java.util.Map<UUID, String> paths = new java.util.HashMap<>();
        p.getImages().forEach(i -> paths.put(i.getCollabPieceImageId(), i.getStoragePath()));
        form.getImages().removeIf(r -> r.getId() == null || !paths.containsKey(r.getId()));
        form.getImages().forEach(r -> r.setThumbUrl(storage.getThumbPublicUrl(paths.get(r.getId()))));
    }

    // ------------------------------------------------------------------
    // Prendas: guardar y borrar
    // ------------------------------------------------------------------

    @Transactional
    public UUID createPiece(UUID brandId, PieceForm form) {
        Collaboration brand = findBrand(brandId);
        OffsetDateTime now = OffsetDateTime.now(clock);

        CollabPiece p = new CollabPiece();
        p.setCollabPieceId(UUID.randomUUID());
        p.setCollaboration(brand);
        p.setCreatedAt(now);
        apply(p, form, now);
        brand.getPieces().add(p);
        return p.getCollabPieceId();
    }

    @Transactional
    public void updatePiece(UUID pieceId, PieceForm form) {
        CollabPiece p = findPiece(pieceId);
        apply(p, form, OffsetDateTime.now(clock));
        pieces.save(p);
    }

    @Transactional
    public void deletePiece(UUID pieceId) {
        CollabPiece p = findPiece(pieceId);
        List<String> paths = p.getImages().stream().map(CollabPieceImage::getStoragePath).toList();
        p.getCollaboration().getPieces().remove(p);
        pieces.delete(p);
        deleteAfterCommit(paths);
    }

    private void apply(CollabPiece p, PieceForm form, OffsetDateTime now) {
        p.setName(form.getName().trim());
        p.setDescription(blankToNull(form.getDescription()));
        p.setUpdatedAt(now);
        syncColors(p, form);
        syncImages(p, form, now);
    }

    private void syncColors(CollabPiece p, PieceForm form) {
        Set<UUID> wanted = new LinkedHashSet<>(form.getColorIds());
        Set<Color> found = new LinkedHashSet<>(colors.findAllById(wanted));
        if (found.size() != wanted.size()) {
            throw new IllegalArgumentException("Color no encontrado");
        }
        p.getColors().clear();
        p.getColors().addAll(found);
    }

    private void syncImages(CollabPiece p, PieceForm form, OffsetDateTime now) {

        Set<UUID> toDelete = new HashSet<>();
        form.getImages().forEach(r -> {
            if (r.isDelete() && r.getId() != null) {
                toDelete.add(r.getId());
            }
        });

        // Se borran del storage solo si la transacción se confirma
        List<String> removed = new ArrayList<>();
        p.getImages().removeIf(img -> {
            if (toDelete.contains(img.getCollabPieceImageId())) {
                removed.add(img.getStoragePath());
                return true;
            }
            return false;
        });

        List<MultipartFile> uploads = form.getNewImages().stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (p.getImages().size() + uploads.size() > PieceForm.MAX_IMAGES) {
            throw new IllegalArgumentException("Máximo " + PieceForm.MAX_IMAGES + " fotos por prenda");
        }

        short nextOrder = (short) p.getImages().stream()
                .mapToInt(i -> i.getSortOrder() == null ? 0 : i.getSortOrder() + 1)
                .max().orElse(0);

        List<String> uploaded = new ArrayList<>();
        cleanupOnRollback(uploaded);

        for (MultipartFile file : uploads) {
            String path = "collaborations/" + p.getCollabPieceId() + "/" + UUID.randomUUID() + ".webp";
            storage.upload(file, path);
            uploaded.add(path);

            CollabPieceImage img = new CollabPieceImage();
            img.setCollabPieceImageId(UUID.randomUUID());
            img.setPiece(p);
            img.setStoragePath(path);
            img.setSortOrder(nextOrder++);
            img.setCreatedAt(now);
            p.getImages().add(img);
        }

        // Principal: la elegida si sigue existiendo; si no, la primera
        String primary = resolvePrimary(p, form.getPrimary(), uploaded);
        if (primary == null && !p.getImages().isEmpty()) {
            primary = p.getImages().get(0).getStoragePath();
        }
        p.setPrimaryImagePath(primary);

        deleteAfterCommit(removed);
    }

    /** Interpreta "existing:<id>" / "new:<n>"; devuelve la ruta o null si no es válida. */
    private String resolvePrimary(CollabPiece p, String choice, List<String> uploaded) {
        if (choice == null) {
            return null;
        }
        try {
            if (choice.startsWith("existing:")) {
                UUID id = UUID.fromString(choice.substring("existing:".length()));
                return p.getImages().stream().filter(i -> id.equals(i.getCollabPieceImageId()))
                        .map(CollabPieceImage::getStoragePath).findFirst().orElse(null);
            }
            if (choice.startsWith("new:")) {
                int index = Integer.parseInt(choice.substring("new:".length()));
                return index >= 0 && index < uploaded.size() ? uploaded.get(index) : null;
            }
        } catch (IllegalArgumentException ignored) {
            // valor manipulado: se usa la primera foto
        }
        return null;
    }

    private CollabPiece findPiece(UUID id) {
        return pieces.findById(id).orElseThrow(() -> new IllegalArgumentException("Prenda no encontrada"));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Si la transacción falla, borra del storage lo que se alcanzó a subir. */
    private void cleanupOnRollback(List<String> uploaded) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    uploaded.forEach(storage::delete);
                }
            }
        });
    }

    private void deleteAfterCommit(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paths.forEach(storage::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paths.forEach(path -> {
                    try {
                        storage.delete(path);
                    } catch (RuntimeException e) {
                        log.warn("No se pudo borrar {}: {}", path, e.getMessage());
                    }
                });
            }
        });
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
