package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.ProductFilter;
import com.quadmlabs.saphikuna.dto.ProductForm;
import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductCollection;
import com.quadmlabs.saphikuna.entity.ProductCollectionId;
import com.quadmlabs.saphikuna.entity.ProductColor;
import com.quadmlabs.saphikuna.entity.ProductImage;
import com.quadmlabs.saphikuna.entity.ProductSize;
import com.quadmlabs.saphikuna.entity.ProductSpecification;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.SettingsService;
import com.quadmlabs.saphikuna.service.StorageService;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final String CODE_PREFIX = "SPK-";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CollectionRepository collectionRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final StorageService storageService;
    private final SettingsService settingsService;
    private final Clock clock;
    private final int maxImages;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            CollectionRepository collectionRepository,
            ColorRepository colorRepository,
            SizeRepository sizeRepository,
            StorageService storageService,
            SettingsService settingsService,
            Clock clock,
            @Value("${app.products.max-images:4}") int maxImages) {

        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.collectionRepository = collectionRepository;
        this.colorRepository = colorRepository;
        this.sizeRepository = sizeRepository;
        this.storageService = storageService;
        this.settingsService = settingsService;
        this.clock = clock;
        this.maxImages = maxImages;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<Product> search(ProductFilter f, Pageable pageable) {

        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            if (f.q() != null && !f.q().isBlank()) {
                // % y _ escritos por el usuario se buscan tal cual, no como comodines
                String like = "%" + escapeLike(f.q().trim().toLowerCase()) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("code"), "")), like, '\\')));
            }
            if (f.categoryId() != null) {
                where.add(cb.equal(root.get("category").get("categoryId"), f.categoryId()));
            }
            if (f.collectionId() != null) {
                // Subconsulta para no duplicar filas ni romper la paginación
                var sub = query.subquery(UUID.class);
                var pc = sub.from(ProductCollection.class);
                sub.select(pc.get("product").<UUID>get("productId"))
                        .where(cb.equal(pc.get("collection").get("collectionId"), f.collectionId()));
                where.add(root.<UUID>get("productId").in(sub));
            }
            if (f.status() != null && !f.status().isBlank()) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.availability() != null && !f.availability().isBlank()) {
                where.add(cb.equal(root.get("availability"), f.availability()));
            }
            if (f.onlyNew()) {
                where.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("newUntil"), LocalDate.now(clock)));
            }
            if (f.missing() != null && !f.missing().isBlank()) {
                Predicate missing = missingPredicate(f.missing(), root, cb);
                if (missing != null) {
                    where.add(missing);
                }
            }
            return cb.and(where.toArray(new Predicate[0]));
        };

        Page<Product> page = productRepository.findAll(spec, pageable);

        // Inicializa lo que el listado muestra (sin depender del open-in-view)
        page.forEach(p -> {
            if (p.getCategory() != null) {
                p.getCategory().getName();
            }
            p.getProductColors().forEach(pc -> pc.getColor().getName());
            p.getProductSizes().forEach(ps -> ps.getSize().getName());
            p.getProductCollections().forEach(pc -> pc.getCollection().getName());
        });

        return page;
    }

    /** Lo que le falta al producto; null si el criterio no existe. */
    private static Predicate missingPredicate(String kind,
            jakarta.persistence.criteria.Root<Product> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
        return switch (kind) {
            case "photo" -> cb.or(cb.isNull(root.get("primaryImagePath")), cb.equal(cb.trim(root.<String>get("primaryImagePath")), ""));
            case "description" -> cb.or(cb.isNull(root.get("description")), cb.equal(cb.trim(root.<String>get("description")), ""));
            case "category" -> cb.isNull(root.get("category"));
            case "colors" -> cb.isEmpty(root.<java.util.Collection<?>>get("productColors"));
            case "sizes" -> cb.isEmpty(root.<java.util.Collection<?>>get("productSizes"));
            default -> null;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public long countMissing(String kind) {
        return productRepository.count((root, query, cb) -> {
            Predicate p = missingPredicate(kind, root, cb);
            return p == null ? cb.disjunction() : p;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ProductStats stats() {
        long total = productRepository.count();
        long visible = productRepository.countByStatus(Product.ACTIVE);
        return new ProductStats(
                total,
                visible,
                total - visible,
                productRepository.countByAvailability(Product.AVAILABLE),
                productRepository.countByAvailability(Product.ON_REQUEST),
                productRepository.countByAvailability(Product.SOLD_OUT),
                productRepository.countByDiscountPercentGreaterThan(BigDecimal.ZERO),
                productRepository.countByNewUntilGreaterThanEqual(LocalDate.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> recent(int limit) {
        return productRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, limit,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt")))
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
    }

    // ------------------------------------------------------------------
    // Formularios
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ProductForm newForm() {
        ProductForm form = new ProductForm();
        form.setDiscountPercent(BigDecimal.ZERO);
        // Un producto nuevo se muestra como "nuevo" durante los días configurados (se puede cambiar o vaciar)
        form.setNewUntil(LocalDate.now(clock).plusDays(settingsService.newProductDays()));
        padSpecs(form, 3);
        return form;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductForm toForm(UUID productId) {

        Product p = findById(productId);
        ProductForm form = new ProductForm();

        form.setName(p.getName());
        form.setCode(p.getCode());
        form.setLabel(p.getLabel());
        form.setDescription(p.getDescription());
        form.setGeneralPrice(p.getGeneralPrice());
        form.setDiscountPercent(p.getDiscountPercent());
        form.setActive(p.isActive());
        form.setAvailability(p.getAvailability());
        form.setNewUntil(p.getNewUntil());
        form.setCategoryId(p.getCategory() != null ? p.getCategory().getCategoryId() : null);

        p.getProductCollections().stream().findFirst()
                .ifPresent(pc -> form.setCollectionId(pc.getCollection().getCollectionId()));

        for (ProductColor pc : p.getProductColors()) {
            (pc.isPrimary() ? form.getPrimaryColorIds() : form.getSecondaryColorIds())
                    .add(pc.getColor().getColorId());
        }

        for (ProductSize ps : p.getProductSizes()) {
            form.getSizeIds().add(ps.getSize().getSizeId());
            if (ps.getPrice() != null && form.getSpecialSizeId() == null) {
                form.setSpecialSizeId(ps.getSize().getSizeId());
                form.setSpecialSizePrice(ps.getPrice());
            }
        }

        for (ProductSpecification s : p.getProductSpecifications()) {
            ProductForm.SpecRow row = new ProductForm.SpecRow();
            row.setName(s.getName());
            row.setValue(s.getValue());
            form.getSpecs().add(row);
        }
        padSpecs(form, 2);

        for (ProductImage img : p.getProductImages()) {
            ProductForm.ImageRow row = new ProductForm.ImageRow();
            row.setId(img.getProductImageId());
            row.setThumbUrl(storageService.getThumbPublicUrl(img.getStoragePath()));
            form.getImages().add(row);
            if (img.getStoragePath().equals(p.getPrimaryImagePath())) {
                form.setPrimary("existing:" + img.getProductImageId());
            }
        }

        return form;
    }

    @Override
    @Transactional(readOnly = true)
    public void decorate(ProductForm form, UUID productId) {

        padSpecs(form, 3);

        // Miniaturas de las imágenes guardadas (el POST solo trae id y delete)
        if (productId != null) {
            Product p = findById(productId);
            form.setCode(p.getCode()); // el campo es de solo lectura y no se envía en el POST
            Map<UUID, String> paths = new HashMap<>();
            p.getProductImages().forEach(i -> paths.put(i.getProductImageId(), i.getStoragePath()));

            form.getImages().removeIf(r -> r.getId() == null || !paths.containsKey(r.getId()));
            form.getImages().forEach(r -> r.setThumbUrl(storageService.getThumbPublicUrl(paths.get(r.getId()))));
        } else {
            form.getImages().clear();
        }
    }

    private void padSpecs(ProductForm form, int blanks) {
        long blank = form.getSpecs().stream().filter(ProductForm.SpecRow::isBlank).count();
        for (long i = blank; i < blanks; i++) {
            form.getSpecs().add(new ProductForm.SpecRow());
        }
    }

    // ------------------------------------------------------------------
    // Guardar / borrar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UUID create(ProductForm form) {
        return create(form, null);
    }

    @Override
    @Transactional
    public UUID create(ProductForm form, String code) {
        Product product = new Product();
        product.setProductId(UUID.randomUUID());
        product.setCreatedAt(OffsetDateTime.now());

        if (code != null && !code.isBlank()) {
            if (productRepository.existsByCodeIgnoreCase(code.trim())) {
                throw new IllegalArgumentException("El código " + code.trim() + " ya está en uso");
            }
            product.setCode(code.trim());
        }

        apply(product, form);
        return product.getProductId();
    }

    @Override
    @Transactional
    public void update(UUID productId, ProductForm form) {
        apply(findById(productId), form);
    }

    private void apply(Product product, ProductForm form) {

        OffsetDateTime now = OffsetDateTime.now();

        // El código NO viene del formulario: lo asigna el servidor (ver más abajo)
        product.setName(form.getName().trim());
        product.setLabel(blankToNull(form.getLabel()));
        product.setDescription(blankToNull(form.getDescription()));
        product.setGeneralPrice(form.getGeneralPrice());
        product.setDiscountPercent(form.getDiscountPercent() == null ? BigDecimal.ZERO : form.getDiscountPercent());
        product.setStatus(form.isActive() ? Product.ACTIVE : Product.INACTIVE);
        product.setAvailability(normalizeAvailability(form.getAvailability()));
        product.setNewUntil(form.getNewUntil());
        product.setUpdatedAt(now);

        product.setCategory(form.getCategoryId() == null ? null
                : categoryRepository.findById(form.getCategoryId())
                        .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada")));

        syncCollections(product, form);
        syncColors(product, form);
        syncSizes(product, form);
        syncSpecs(product, form);
        syncImages(product, form, now);

        // Código secuencial: se pide al final para no gastar números si algo falla antes.
        // Los productos que ya tienen código (por ejemplo los de la tienda) lo conservan.
        if (product.getCode() == null) {
            product.setCode(allocateCode());
            productRepository.save(product);
        }
    }

    /** Siguiente código libre (SPK-021, SPK-022...). La secuencia nunca repite un número. */
    private String allocateCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = String.format("%s%03d", CODE_PREFIX, productRepository.nextCodeNumber());
            // Por si ya existe (código puesto a mano o importado por encima de la secuencia)
            if (!productRepository.existsByCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo asignar un código de producto");
    }

    private void syncCollections(Product product, ProductForm form) {

        Set<UUID> wanted = new LinkedHashSet<>();
        if (form.getCollectionId() != null) {
            wanted.add(form.getCollectionId());
        }

        product.getProductCollections()
                .removeIf(pc -> !wanted.contains(pc.getCollection().getCollectionId()));

        Set<UUID> present = new HashSet<>();
        product.getProductCollections().forEach(pc -> present.add(pc.getCollection().getCollectionId()));

        for (UUID collectionId : wanted) {
            if (present.contains(collectionId)) {
                continue;
            }
            Collection collection = collectionRepository.findById(collectionId)
                    .orElseThrow(() -> new IllegalArgumentException("Colección no encontrada"));

            ProductCollectionId id = new ProductCollectionId();
            id.setProductId(product.getProductId());
            id.setCollectionId(collectionId);

            ProductCollection pc = new ProductCollection();
            pc.setId(id);
            pc.setProduct(product);
            pc.setCollection(collection);
            product.getProductCollections().add(pc);
        }
    }

    private void syncColors(Product product, ProductForm form) {

        // Si un color llega como principal y secundario, gana el principal
        Map<UUID, Boolean> wanted = new LinkedHashMap<>();
        form.getSecondaryColorIds().forEach(id -> wanted.put(id, false));
        form.getPrimaryColorIds().forEach(id -> wanted.put(id, true));

        product.getProductColors().removeIf(pc -> !wanted.containsKey(pc.getColor().getColorId()));

        Map<UUID, ProductColor> present = new HashMap<>();
        product.getProductColors().forEach(pc -> present.put(pc.getColor().getColorId(), pc));

        wanted.forEach((colorId, primary) -> {
            ProductColor pc = present.get(colorId);
            if (pc == null) {
                pc = new ProductColor();
                pc.setProductColorId(UUID.randomUUID());
                pc.setProduct(product);
                pc.setColor(colorRepository.findById(colorId)
                        .orElseThrow(() -> new IllegalArgumentException("Color no encontrado")));
                product.getProductColors().add(pc);
            }
            pc.setPrimary(primary);
        });
    }

    private void syncSizes(Product product, ProductForm form) {

        Set<UUID> wanted = new LinkedHashSet<>(form.getSizeIds());
        if (form.getSpecialSizeId() != null) {
            wanted.add(form.getSpecialSizeId());
        }

        product.getProductSizes().removeIf(ps -> !wanted.contains(ps.getSize().getSizeId()));

        Map<UUID, ProductSize> present = new HashMap<>();
        product.getProductSizes().forEach(ps -> present.put(ps.getSize().getSizeId(), ps));

        for (UUID sizeId : wanted) {
            ProductSize ps = present.get(sizeId);
            if (ps == null) {
                ps = new ProductSize();
                ps.setProductSizeId(UUID.randomUUID());
                ps.setProduct(product);
                ps.setSize(sizeRepository.findById(sizeId)
                        .orElseThrow(() -> new IllegalArgumentException("Talla no encontrada")));
                product.getProductSizes().add(ps);
            }
            // Solo la talla especial lleva precio propio
            boolean special = sizeId.equals(form.getSpecialSizeId())
                    && form.getSpecialSizePrice() != null
                    && form.getSpecialSizePrice().compareTo(product.getGeneralPrice()) != 0;
            ps.setPrice(special ? form.getSpecialSizePrice() : null);
        }
    }

    private void syncSpecs(Product product, ProductForm form) {

        // Sin restricción única: se reemplazan completas
        product.getProductSpecifications().clear();

        short order = 0;
        for (ProductForm.SpecRow row : form.getSpecs()) {
            if (row.getName() == null || row.getName().isBlank()
                    || row.getValue() == null || row.getValue().isBlank()) {
                continue;
            }
            ProductSpecification s = new ProductSpecification();
            s.setProductSpecificationId(UUID.randomUUID());
            s.setProduct(product);
            s.setName(row.getName().trim());
            s.setValue(row.getValue().trim());
            s.setSortOrder(order++);
            product.getProductSpecifications().add(s);
        }
    }

    private void syncImages(Product product, ProductForm form, OffsetDateTime now) {

        Set<UUID> toDelete = new HashSet<>();
        form.getImages().forEach(r -> {
            if (r.isDelete() && r.getId() != null) {
                toDelete.add(r.getId());
            }
        });

        // Se borran del storage solo si la transacción se confirma
        List<String> removedPaths = new ArrayList<>();
        product.getProductImages().removeIf(img -> {
            if (toDelete.contains(img.getProductImageId())) {
                removedPaths.add(img.getStoragePath());
                return true;
            }
            return false;
        });

        List<MultipartFile> uploads = form.getNewImages().stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (product.getProductImages().size() + uploads.size() > maxImages) {
            throw new IllegalArgumentException("Máximo " + maxImages + " imágenes por producto");
        }

        short nextOrder = (short) product.getProductImages().stream()
                .mapToInt(i -> i.getSortOrder() == null ? 0 : i.getSortOrder() + 1)
                .max().orElse(0);

        List<String> uploadedPaths = new ArrayList<>();
        cleanupOnRollback(uploadedPaths);

        for (MultipartFile file : uploads) {
            String path = "products/" + product.getProductId() + "/" + UUID.randomUUID() + ".webp";
            storageService.upload(file, path);
            uploadedPaths.add(path);

            ProductImage img = new ProductImage();
            img.setProductImageId(UUID.randomUUID());
            img.setProduct(product);
            img.setStoragePath(path);
            img.setSortOrder(nextOrder++);
            img.setCreatedAt(now);
            product.getProductImages().add(img);
        }

        // Principal: la elegida si sigue existiendo; si no, la primera
        String primary = resolvePrimary(product, form.getPrimary(), uploadedPaths);
        if (primary == null && !product.getProductImages().isEmpty()) {
            primary = product.getProductImages().get(0).getStoragePath();
        }
        product.setPrimaryImagePath(primary);

        productRepository.save(product);

        deleteAfterCommit(removedPaths);
    }

    /** Interpreta "existing:<id>" / "new:<n>"; devuelve la ruta o null si no es válida. */
    private String resolvePrimary(Product product, String choice, List<String> uploadedPaths) {
        if (choice == null) {
            return null;
        }
        try {
            if (choice.startsWith("existing:")) {
                UUID id = UUID.fromString(choice.substring("existing:".length()));
                return product.getProductImages().stream()
                        .filter(i -> id.equals(i.getProductImageId()))
                        .map(ProductImage::getStoragePath)
                        .findFirst().orElse(null);
            }
            if (choice.startsWith("new:")) {
                int index = Integer.parseInt(choice.substring("new:".length()));
                return index >= 0 && index < uploadedPaths.size() ? uploadedPaths.get(index) : null;
            }
        } catch (IllegalArgumentException ignored) {
            // valor manipulado: se usa la primera foto
        }
        return null;
    }

    @Override
    @Transactional
    public void delete(UUID productId) {
        Product product = findById(productId);
        List<String> paths = product.getProductImages().stream().map(ProductImage::getStoragePath).toList();
        productRepository.delete(product);
        deleteAfterCommit(paths);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Si la transacción falla, borra del storage lo que se alcanzó a subir. */
    private void cleanupOnRollback(List<String> uploadedPaths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    uploadedPaths.forEach(storageService::delete);
                }
            }
        });
    }

    private void deleteAfterCommit(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paths.forEach(storageService::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paths.forEach(path -> {
                    try {
                        storageService.delete(path);
                    } catch (RuntimeException e) {
                        log.warn("No se pudo borrar {}: {}", path, e.getMessage());
                    }
                });
            }
        });
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalizeAvailability(String a) {
        if (Product.SOLD_OUT.equals(a) || Product.ON_REQUEST.equals(a)) {
            return a;
        }
        return Product.AVAILABLE;
    }
}
