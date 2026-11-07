package com.quadmlabs.saphikuna.tools;

import com.quadmlabs.saphikuna.dto.ProductForm;
import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductColorRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.StorageService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Importación ÚNICA de los productos de la tienda (data/products.json) a la base.
 *
 * No se ejecuta con la app normal: solo si se arranca con
 * {@code --app.import.enabled=true}. Por defecto es una SIMULACIÓN
 * ({@code app.import.dry-run=true}): lee todo, procesa las fotos en memoria y
 * muestra qué haría, sin escribir en la base ni en el Storage.
 *
 * Es repetible: los productos cuyo código ya existe se omiten.
 * Se puede borrar esta clase cuando termine la migración.
 */
@Component
@ConditionalOnProperty(name = "app.import.enabled", havingValue = "true")
public class ProductImporter implements CommandLineRunner {

    private static final int MAX_IMAGES = ProductForm.MAX_IMAGES;

    /** Catálogo de colores de la tienda con su hex (se usa en la tabla y el formulario). */
    private static final Map<String, String> COLOR_HEX = new LinkedHashMap<>();

    static {
        COLOR_HEX.put("Blanco", "#FFFFFF");
        COLOR_HEX.put("Negro", "#1A1A1A");
        COLOR_HEX.put("Beige", "#F5F5DC");
        COLOR_HEX.put("Café", "#6F4E37");
        COLOR_HEX.put("Rojo", "#D32F2F");
        COLOR_HEX.put("Vino", "#722F37");
        COLOR_HEX.put("Ladrillo", "#B5472F");
        COLOR_HEX.put("Tomate", "#FF6347");
        COLOR_HEX.put("Coral", "#FF7F50");
        COLOR_HEX.put("Mostaza", "#E1AD01");
        COLOR_HEX.put("Verde olivo", "#708238");
        COLOR_HEX.put("Turquesa", "#30D5C8");
        COLOR_HEX.put("Celeste", "#87CEEB");
        COLOR_HEX.put("Azul rey", "#4169E1");
        COLOR_HEX.put("Lila", "#C8A2C8");
        COLOR_HEX.put("Púrpura", "#7E3F98");
        COLOR_HEX.put("Fucsia", "#E6007E");
        COLOR_HEX.put("Palo de rosa", "#D8A7B1");
    }

    /** Nombres mal escritos en la base → nombre correcto (claves ya normalizadas). */
    private static final Map<String, String> COLOR_ALIAS = Map.of("baige", "beige");

    /** Textos del JSON que mezclan varios colores. */
    private static final Map<String, List<String>> COLOR_SPLIT = Map.of(
            "beige con lila y cafe con beige", List.of("Beige", "Lila", "Café"));

    /** Categoría del JSON → categoría de la base. */
    private static final Map<String, String> CATEGORY_MAP = Map.of(
            "Suéter", "Chompas",
            "Blusa", "Blusas",
            "Chaleco", "Chalecos",
            "Chaqueta", "Chaquetas",
            "Accesorios", "Accesorios");

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final ProductColorRepository productColorRepository;
    private final StorageService storageService;
    private final Validator validator;

    private final Path storeRoot;
    private final boolean dryRun;
    private final boolean pruneColors;
    private final Path reportFile;

    private final List<String> report = new ArrayList<>();

    public ProductImporter(
            ProductService productService,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ColorRepository colorRepository,
            SizeRepository sizeRepository,
            ProductColorRepository productColorRepository,
            StorageService storageService,
            Validator validator,
            @Value("${app.import.path}") String storePath,
            @Value("${app.import.dry-run:true}") boolean dryRun,
            @Value("${app.import.prune-colors:false}") boolean pruneColors,
            @Value("${app.import.report:}") String reportPath) {

        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.colorRepository = colorRepository;
        this.sizeRepository = sizeRepository;
        this.productColorRepository = productColorRepository;
        this.storageService = storageService;
        this.validator = validator;
        this.storeRoot = Path.of(storePath);
        this.dryRun = dryRun;
        this.pruneColors = pruneColors;
        this.reportFile = reportPath.isBlank() ? null : Path.of(reportPath);
    }

    @Override
    public void run(String... args) throws Exception {

        line("=== IMPORTACIÓN DE PRODUCTOS " + (dryRun ? "(SIMULACIÓN: no se escribe nada)" : "(REAL)") + " ===");
        line("Origen: " + storeRoot);

        JsonNode products = JsonMapper.builder().build()
                .readTree(Files.readString(storeRoot.resolve("data").resolve("products.json"), StandardCharsets.UTF_8));

        syncColors();
        Map<String, Category> categories = syncCategories(products);
        Map<String, UUID> colorIds = new HashMap<>();
        colorRepository.findAll().forEach(c -> colorIds.put(key(c.getName()), c.getColorId()));
        Map<String, UUID> sizeIds = new HashMap<>();
        for (Size s : sizeRepository.findAll()) {
            sizeIds.put(key(s.getName()), s.getSizeId());
        }

        int created = 0, skipped = 0, failed = 0;
        long totalMain = 0, totalThumb = 0;
        int totalImages = 0;

        line("");
        line("--- PRODUCTOS ---");

        for (JsonNode p : products) {
            String code = p.path("code").asText();

            if (productRepository.existsByCodeIgnoreCase(code)) {
                line(String.format("%-8s OMITIDO: ya existe un producto con ese código", code));
                skipped++;
                continue;
            }

            try {
                List<String> warnings = new ArrayList<>();
                ProductForm form = buildForm(p, categories, colorIds, sizeIds, warnings);

                // Imágenes (máx. 4, asegurando que la principal esté entre ellas)
                List<String> imagePaths = pickImages(p, warnings);
                int primaryIndex = Math.max(0, imagePaths.indexOf(p.path("primaryImage").asText()));
                form.setPrimary("new:" + primaryIndex);

                long main = 0, thumb = 0;
                List<String> sizesInfo = new ArrayList<>();
                for (String rel : imagePaths) {
                    FileMultipartFile file = new FileMultipartFile(storeRoot.resolve(rel.replace('/', java.io.File.separatorChar)));
                    form.getNewImages().add(file);
                    if (dryRun) {
                        StorageService.ImageStats st = storageService.inspect(file);
                        main += st.mainBytes();
                        thumb += st.thumbBytes();
                        sizesInfo.add(file.getSize() / 1024 + "KB→" + st.mainBytes() / 1024 + "KB(" + st.width() + "x" + st.height() + ")");
                    }
                }

                for (ConstraintViolation<ProductForm> v : validator.validate(form)) {
                    warnings.add("VALIDACIÓN " + v.getPropertyPath() + ": " + v.getMessage());
                }

                line(String.format("%-8s %-28s %-12s $%-6s | colores: %s | tallas: %s | %d foto(s)%s",
                        code, p.path("name").asText(), CATEGORY_MAP.getOrDefault(p.path("category").asText(), "?"),
                        p.path("price").decimalValue().toPlainString(),
                        colorsLabel(form, colorIds), p.path("sizes").size() + " tallas", imagePaths.size(),
                        p.path("label").isNull() ? "" : " | etiqueta " + p.path("label").asText()));
                if (dryRun && !sizesInfo.isEmpty()) {
                    line("         fotos: " + String.join(", ", sizesInfo));
                }
                warnings.forEach(w -> line("         ⚠ " + w));

                if (!dryRun) {
                    productService.create(form, code);
                }

                created++;
                totalImages += imagePaths.size();
                totalMain += main;
                totalThumb += thumb;

            } catch (Exception e) {
                failed++;
                line(String.format("%-8s ERROR: %s", code, e.getMessage()));
            }
        }

        line("");
        line("=== RESUMEN ===");
        line((dryRun ? "Se crearían: " : "Creados: ") + created + " | omitidos: " + skipped + " | con error: " + failed);
        if (dryRun) {
            line("Fotos: " + totalImages + " | tamaño final estimado: "
                    + String.format("%.1f MB principales + %.1f MB miniaturas", totalMain / 1048576.0, totalThumb / 1048576.0));
        }

        if (reportFile != null) {
            Files.write(reportFile, report, StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------
    // Catálogos
    // ------------------------------------------------------------------

    private void syncColors() {
        line("--- COLORES ---");

        Map<String, Color> existing = new LinkedHashMap<>();
        for (Color c : colorRepository.findAll()) {
            existing.put(canonicalKey(c.getName()), c);
        }

        Set<String> wanted = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : COLOR_HEX.entrySet()) {
            String name = e.getKey();
            String hex = e.getValue();
            wanted.add(key(name));

            Color c = existing.get(key(name));
            if (c == null) {
                line("  CREAR        " + name + " " + hex);
                if (!dryRun) {
                    Color nc = new Color();
                    nc.setColorId(UUID.randomUUID());
                    nc.setName(name);
                    nc.setHexCode(hex);
                    nc.setCreatedAt(OffsetDateTime.now());
                    colorRepository.save(nc);
                }
                continue;
            }

            List<String> changes = new ArrayList<>();
            if (!name.equals(c.getName())) {
                changes.add("nombre '" + c.getName() + "' → '" + name + "'");
                c.setName(name);
            }
            if (c.getHexCode() == null || c.getHexCode().isBlank()) {
                changes.add("hex " + hex);
                c.setHexCode(hex);
            }
            if (changes.isEmpty()) {
                line("  SIN CAMBIOS  " + name + " " + c.getHexCode());
            } else {
                line("  ACTUALIZAR   " + name + ": " + String.join(", ", changes));
                if (!dryRun) {
                    colorRepository.save(c);
                }
            }
        }

        for (Map.Entry<String, Color> e : existing.entrySet()) {
            if (wanted.contains(e.getKey())) {
                continue;
            }
            Color c = e.getValue();
            boolean used = productColorRepository.existsByColor(c);
            if (used) {
                line("  CONSERVADO   " + c.getName() + " (no está en el JSON pero un producto lo usa)");
            } else if (pruneColors) {
                line("  ELIMINAR     " + c.getName() + " (no está en el JSON y nadie lo usa)");
                if (!dryRun) {
                    colorRepository.delete(c);
                }
            } else {
                line("  SOBRANTE     " + c.getName() + " (no está en el JSON; se eliminaría con prune-colors=true)");
            }
        }
    }

    private Map<String, Category> syncCategories(JsonNode products) {
        line("");
        line("--- CATEGORÍAS ---");

        Map<String, Category> byKey = new HashMap<>();
        categoryRepository.findAll().forEach(c -> byKey.put(key(c.getName()), c));

        Set<String> needed = new LinkedHashSet<>();
        for (JsonNode p : products) {
            needed.add(CATEGORY_MAP.getOrDefault(p.path("category").asText(), p.path("category").asText()));
        }

        for (String name : needed) {
            if (byKey.containsKey(key(name))) {
                line("  EXISTE       " + name);
                continue;
            }
            line("  CREAR        " + name);
            if (!dryRun) {
                Category c = new Category();
                c.setCategoryId(UUID.randomUUID());
                c.setName(name);
                c.setCreatedAt(OffsetDateTime.now());
                byKey.put(key(name), categoryRepository.save(c));
            }
        }
        return byKey;
    }

    // ------------------------------------------------------------------
    // Producto → formulario
    // ------------------------------------------------------------------

    private ProductForm buildForm(JsonNode p, Map<String, Category> categories, Map<String, UUID> colorIds,
            Map<String, UUID> sizeIds, List<String> warnings) {

        ProductForm f = new ProductForm();
        f.setName(p.path("name").asText().trim());
        f.setDescription(p.path("description").isNull() ? null : p.path("description").asText());
        f.setGeneralPrice(p.path("price").decimalValue());
        f.setLabel(p.path("label").isNull() ? null : p.path("label").asText());
        f.setActive(true);
        f.setAvailability("EN STOCK".equalsIgnoreCase(p.path("status").asText())
                ? Product.AVAILABLE : Product.SOLD_OUT);

        // underlinedPrice = precio anterior tachado → porcentaje de descuento
        if (!p.path("underlinedPrice").isNull() && p.path("underlinedPrice").decimalValue().signum() > 0) {
            java.math.BigDecimal before = p.path("underlinedPrice").decimalValue();
            java.math.BigDecimal now = p.path("price").decimalValue();
            f.setGeneralPrice(before);
            f.setDiscountPercent(before.subtract(now).multiply(java.math.BigDecimal.valueOf(100))
                    .divide(before, 0, java.math.RoundingMode.HALF_UP));
        }

        String categoryName = CATEGORY_MAP.getOrDefault(p.path("category").asText(), p.path("category").asText());
        Category category = categories.get(key(categoryName));
        if (category != null) {
            f.setCategoryId(category.getCategoryId());
        } else if (!dryRun) {
            warnings.add("categoría sin resolver: " + categoryName);
        }

        for (String name : colorNames(p.path("colors").path("primary"), warnings)) {
            addColor(f.getPrimaryColorIds(), name, colorIds);
        }
        for (String name : colorNames(p.path("colors").path("secondary"), warnings)) {
            addColor(f.getSecondaryColorIds(), name, colorIds);
        }

        for (JsonNode s : p.path("sizes")) {
            UUID id = sizeIds.get(key(s.asText()));
            if (id == null) {
                warnings.add("talla desconocida: " + s.asText());
            } else {
                f.getSizeIds().add(id);
            }
        }

        for (JsonNode spec : p.path("specifications")) {
            ProductForm.SpecRow row = new ProductForm.SpecRow();
            row.setName(spec.path("name").asText());
            row.setValue(spec.path("value").asText());
            f.getSpecs().add(row);
        }

        return f;
    }

    private void addColor(List<UUID> target, String name, Map<String, UUID> colorIds) {
        UUID id = colorIds.get(key(name));
        // En simulación los colores nuevos aún no tienen id: se usa uno de relleno
        target.add(id != null ? id : UUID.nameUUIDFromBytes(("simulado:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    private List<String> colorNames(JsonNode array, List<String> warnings) {
        List<String> names = new ArrayList<>();
        for (JsonNode n : array) {
            String raw = n.asText();
            List<String> special = COLOR_SPLIT.get(key(raw));
            List<String> parts = new ArrayList<>();
            if (special != null) {
                parts.addAll(special);
            } else {
                for (String part : raw.split(",")) {
                    parts.add(part.trim());
                }
            }
            for (String part : parts) {
                String canonical = COLOR_HEX.keySet().stream()
                        .filter(c -> key(c).equals(key(part))).findFirst().orElse(null);
                if (canonical == null) {
                    warnings.add("color sin hex definido, se omite: '" + part + "'");
                } else if (!names.contains(canonical)) {
                    names.add(canonical);
                }
            }
        }
        return names;
    }

    private String colorsLabel(ProductForm f, Map<String, UUID> colorIds) {
        Map<UUID, String> byId = new HashMap<>();
        COLOR_HEX.keySet().forEach(n -> byId.put(
                colorIds.getOrDefault(key(n), UUID.nameUUIDFromBytes(("simulado:" + n).getBytes(StandardCharsets.UTF_8))), n));
        List<String> names = new ArrayList<>();
        f.getPrimaryColorIds().forEach(id -> names.add(byId.getOrDefault(id, "?")));
        f.getSecondaryColorIds().forEach(id -> names.add(byId.getOrDefault(id, "?") + " (sec.)"));
        return String.join(", ", names);
    }

    /** Máximo 4 fotos; si la principal quedara fuera, reemplaza a la última. */
    private List<String> pickImages(JsonNode p, List<String> warnings) {
        List<String> all = new ArrayList<>();
        p.path("images").forEach(i -> all.add(i.asText()));
        String primary = p.path("primaryImage").asText();

        if (all.size() <= MAX_IMAGES) {
            return all;
        }

        List<String> kept = new ArrayList<>(all.subList(0, MAX_IMAGES));
        if (!kept.contains(primary) && all.contains(primary)) {
            kept.set(MAX_IMAGES - 1, primary);
        }
        List<String> dropped = new ArrayList<>(all);
        dropped.removeAll(kept);
        warnings.add("tiene " + all.size() + " fotos; se importan " + MAX_IMAGES + ", se omite: " + dropped);
        return kept;
    }

    // ------------------------------------------------------------------

    private static String key(String s) {
        String k = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
        return k;
    }

    private static String canonicalKey(String s) {
        String k = key(s);
        return COLOR_ALIAS.getOrDefault(k, k);
    }

    private void line(String text) {
        report.add(text);
        System.out.println(text);
    }
}
