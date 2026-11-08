package com.quadmlabs.saphikuna.brochure.builder;

import com.quadmlabs.saphikuna.dto.ProductFilter;
import com.quadmlabs.saphikuna.entity.Brochure;
import com.quadmlabs.saphikuna.entity.BrochureItem;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductColor;
import com.quadmlabs.saphikuna.entity.ProductImage;
import com.quadmlabs.saphikuna.entity.ProductSize;
import com.quadmlabs.saphikuna.entity.ProductSpecification;
import com.quadmlabs.saphikuna.repository.BrochureRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Armado de catálogos: crear, elegir prendas, y para cada prenda decidir qué fotos y qué textos lleva.
 * Todo lo que se arma queda guardado, así se puede volver a editar o duplicar.
 */
@Service
@Transactional
public class BrochureBuilderService {

    public static final int MAX_EXTRAS = 2;
    public static final int MAX_NAME = 80;
    public static final int MAX_INTRO = 400;
    public static final int MAX_HIGHLIGHT = 450;
    public static final int MAX_DETAILS = 500;

    /** Papel de una foto dentro de la prenda del catálogo. */
    public static final String ROLE_NONE = "none";
    public static final String ROLE_MAIN = "main";
    public static final String ROLE_EXTRA = "extra";

    /** Error de regla; {@code field} es el campo del formulario y {@code getMessage()} la clave del mensaje. */
    public static class BuilderException extends RuntimeException {
        private final String field;

        public BuilderException(String field, String messageKey) {
            super(messageKey);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    // ---------------------------------------------------------------- lo que ven las pantallas

    public record Summary(UUID id, String name, int itemCount, OffsetDateTime updatedAt, String thumbUrl) {
    }

    public record ItemRow(UUID itemId, UUID productId, String code, String name, String thumbUrl, int position,
            int photoCount, boolean textsReady, String availability) {
    }

    public record Overview(UUID id, String name, String introText, boolean showPrices, List<ItemRow> items) {
    }

    public record Candidate(UUID productId, String code, String name, String category, String thumbUrl,
            String availability, int photoCount, boolean alreadyIn) {
    }

    public record PhotoOption(UUID imageId, String thumbUrl, String role) {
    }

    public record ItemEdit(UUID brochureId, UUID itemId, UUID productId, String code, String name, String category,
            String productThumbUrl, List<PhotoOption> photos, ItemForm form, UUID previousItemId, UUID nextItemId,
            int number, int total) {
    }

    /** Una prenda lista para el PDF: todo ya resuelto, sin cargas perezosas. */
    public record PdfItem(String code, String name, String category, String availability, boolean isNew,
            java.math.BigDecimal price, java.math.BigDecimal finalPrice, int discountPercent,
            String mainUrl, List<String> extraUrls, List<String> highlight, List<String> details) {

        public boolean hasDiscount() {
            return discountPercent > 0;
        }

        public boolean isOnRequest() {
            return Product.ON_REQUEST.equals(availability);
        }

        public boolean isSoldOut() {
            return Product.SOLD_OUT.equals(availability);
        }

        public boolean hasExtras() {
            return !extraUrls.isEmpty();
        }
    }

    /** El catálogo listo para convertirse en PDF. */
    public record PdfCatalog(String name, String intro, boolean showPrices, List<PdfItem> items) {
        public boolean hasIntro() {
            return intro != null && !intro.isBlank();
        }
    }

    // ---------------------------------------------------------------- dependencias

    private final BrochureRepository brochures;
    private final ProductRepository products;
    private final ProductService productService;
    private final StorageService storage;
    private final Clock clock;

    public BrochureBuilderService(BrochureRepository brochures, ProductRepository products,
            ProductService productService, StorageService storage, Clock clock) {
        this.brochures = brochures;
        this.products = products;
        this.productService = productService;
        this.storage = storage;
        this.clock = clock;
    }

    // ================================================================ catálogos

    @Transactional(readOnly = true)
    public List<Summary> list() {
        List<Summary> out = new ArrayList<>();
        for (Brochure b : brochures.findAllByOrderByUpdatedAtDesc()) {
            String thumb = b.getItems().isEmpty() ? null : thumbOf(b.getItems().get(0));
            out.add(new Summary(b.getBrochureId(), b.getName(), b.getItems().size(), b.getUpdatedAt(), thumb));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID id) {
        Brochure b = find(id);
        List<ItemRow> rows = new ArrayList<>();
        for (BrochureItem item : b.getItems()) {
            Product p = item.getProduct();
            rows.add(new ItemRow(item.getBrochureItemId(), p.getProductId(), p.getCode(), p.getName(), thumbOf(item),
                    item.getPosition(), (item.getMainImage() == null ? 0 : 1) + item.extras().size(),
                    notBlank(item.getHighlightText()) && notBlank(item.getDetailsText()), p.getAvailability()));
        }
        return new Overview(b.getBrochureId(), b.getName(), b.getIntroText(), b.isShowPrices(), rows);
    }

    @Transactional(readOnly = true)
    public BrochureForm toForm(UUID id) {
        Brochure b = find(id);
        BrochureForm form = new BrochureForm();
        form.setName(b.getName());
        form.setIntroText(b.getIntroText());
        form.setShowPrices(b.isShowPrices());
        return form;
    }

    public UUID create(BrochureForm form) {
        Brochure b = new Brochure();
        b.setBrochureId(UUID.randomUUID());
        b.setCreatedAt(now());
        apply(b, form);
        return brochures.save(b).getBrochureId();
    }

    public void update(UUID id, BrochureForm form) {
        apply(find(id), form);
    }

    private void apply(Brochure b, BrochureForm form) {
        b.setName(form.getName().trim());
        b.setIntroText(blankToNull(form.getIntroText()));
        b.setShowPrices(form.isShowPrices());
        b.setUpdatedAt(now());
    }

    /** Copia el catálogo con sus prendas, fotos y textos: sirve de punto de partida para otro. */
    public UUID duplicate(UUID id) {
        Brochure source = find(id);
        Brochure copy = new Brochure();
        copy.setBrochureId(UUID.randomUUID());
        String name = source.getName() + " (copia)";
        copy.setName(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name);
        copy.setIntroText(source.getIntroText());
        copy.setShowPrices(source.isShowPrices());
        copy.setCreatedAt(now());
        copy.setUpdatedAt(now());
        for (BrochureItem item : source.getItems()) {
            BrochureItem c = new BrochureItem();
            c.setBrochureItemId(UUID.randomUUID());
            c.setBrochure(copy);
            c.setProduct(item.getProduct());
            c.setPosition(item.getPosition());
            c.setMainImage(item.getMainImage());
            c.setExtraImage1(item.getExtraImage1());
            c.setExtraImage2(item.getExtraImage2());
            c.setHighlightText(item.getHighlightText());
            c.setDetailsText(item.getDetailsText());
            copy.getItems().add(c);
        }
        return brochures.save(copy).getBrochureId();
    }

    public void delete(UUID id) {
        brochures.delete(find(id));
    }

    // ================================================================ elegir prendas

    /** Productos visibles que se pueden agregar, marcando los que ya están en el catálogo. */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(UUID brochureId, String q, UUID categoryId) {
        Set<UUID> already = productIdsIn(find(brochureId));
        ProductFilter filter = new ProductFilter(q, categoryId, null, Product.ACTIVE, null, null, false);
        List<Candidate> out = new ArrayList<>();
        for (Product p : productService.search(filter, PageRequest.of(0, 500, Sort.by("code"))).getContent()) {
            out.add(new Candidate(p.getProductId(), p.getCode(), p.getName(),
                    p.getCategory() == null ? null : p.getCategory().getName(), productThumb(p), p.getAvailability(),
                    p.getProductImages().size(), already.contains(p.getProductId())));
        }
        return out;
    }

    /** Agrega las prendas elegidas al final, con fotos y textos de partida. Devuelve cuántas se agregaron. */
    public int addProducts(UUID brochureId, Collection<UUID> productIds) {
        Brochure b = find(brochureId);
        Set<UUID> already = productIdsIn(b);

        List<Product> chosen = new ArrayList<>();
        for (Product p : products.findAllById(productIds == null ? List.of() : productIds)) {
            if (p.isActive() && !already.contains(p.getProductId())) {
                chosen.add(p);
            }
        }
        chosen.sort(Comparator.comparing(Product::getCode, Comparator.nullsLast(String::compareToIgnoreCase)));

        int position = b.getItems().size();
        for (Product p : chosen) {
            b.getItems().add(newItem(b, p, position++));
        }
        if (!chosen.isEmpty()) {
            b.setUpdatedAt(now());
        }
        return chosen.size();
    }

    /** "Seleccionar todas": agrega todas las que cumplen el filtro y aún no están. */
    public int addAll(UUID brochureId, String q, UUID categoryId) {
        List<UUID> ids = candidates(brochureId, q, categoryId).stream()
                .filter(c -> !c.alreadyIn()).map(Candidate::productId).toList();
        return addProducts(brochureId, ids);
    }

    private BrochureItem newItem(Brochure b, Product p, int position) {
        BrochureItem item = new BrochureItem();
        item.setBrochureItemId(UUID.randomUUID());
        item.setBrochure(b);
        item.setProduct(p);
        item.setPosition(position);

        // Fotos de partida: la principal del producto y hasta dos más, en su orden
        List<ProductImage> images = p.getProductImages();
        ProductImage main = images.stream()
                .filter(i -> i.getStoragePath() != null && i.getStoragePath().equals(p.getPrimaryImagePath()))
                .findFirst().orElse(images.isEmpty() ? null : images.get(0));
        item.setMainImage(main);
        List<ProductImage> extras = images.stream().filter(i -> i != main).limit(MAX_EXTRAS).toList();
        item.setExtraImage1(extras.size() > 0 ? extras.get(0) : null);
        item.setExtraImage2(extras.size() > 1 ? extras.get(1) : null);

        item.setHighlightText(defaultHighlight(p));
        item.setDetailsText(defaultDetails(p));
        return item;
    }

    public void removeItem(UUID brochureId, UUID itemId) {
        Brochure b = find(brochureId);
        b.getItems().removeIf(i -> i.getBrochureItemId().equals(itemId));
        renumber(b);
        b.setUpdatedAt(now());
    }

    /** Sube ({@code delta} = -1) o baja ({@code delta} = +1) una prenda en el orden. */
    public void moveItem(UUID brochureId, UUID itemId, int delta) {
        Brochure b = find(brochureId);
        List<BrochureItem> items = b.getItems();
        int index = indexOf(items, itemId);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= items.size()) {
            return;
        }
        BrochureItem moved = items.remove(index);
        items.add(target, moved);
        renumber(b);
        b.setUpdatedAt(now());
    }

    private static void renumber(Brochure b) {
        for (int i = 0; i < b.getItems().size(); i++) {
            b.getItems().get(i).setPosition(i);
        }
    }

    // ================================================================ ficha de la prenda

    @Transactional(readOnly = true)
    public ItemEdit itemEdit(UUID brochureId, UUID itemId) {
        Brochure b = find(brochureId);
        List<BrochureItem> items = b.getItems();
        int index = indexOf(items, itemId);
        if (index < 0) {
            throw new BuilderException(null, "brochures.notfound");
        }
        BrochureItem item = items.get(index);
        Product p = item.getProduct();

        ItemForm form = new ItemForm();
        form.setHighlightText(item.getHighlightText());
        form.setDetailsText(item.getDetailsText());
        form.setRoles(rolesOf(item));

        return build(b, item, p, form, index);
    }

    /** La misma ficha pero con lo que la persona acaba de escribir (para volver a mostrarla con errores). */
    @Transactional(readOnly = true)
    public ItemEdit itemEdit(UUID brochureId, UUID itemId, ItemForm form) {
        Brochure b = find(brochureId);
        int index = indexOf(b.getItems(), itemId);
        if (index < 0) {
            throw new BuilderException(null, "brochures.notfound");
        }
        BrochureItem item = b.getItems().get(index);
        return build(b, item, item.getProduct(), form, index);
    }

    private ItemEdit build(Brochure b, BrochureItem item, Product p, ItemForm form, int index) {
        List<PhotoOption> photos = new ArrayList<>();
        for (ProductImage image : p.getProductImages()) {
            String role = form.getRoles().getOrDefault(image.getProductImageId().toString(), ROLE_NONE);
            photos.add(new PhotoOption(image.getProductImageId(), storage.getThumbPublicUrl(image.getStoragePath()), role));
        }
        List<BrochureItem> items = b.getItems();
        return new ItemEdit(b.getBrochureId(), item.getBrochureItemId(), p.getProductId(), p.getCode(), p.getName(),
                p.getCategory() == null ? null : p.getCategory().getName(), productThumb(p), photos, form,
                index > 0 ? items.get(index - 1).getBrochureItemId() : null,
                index < items.size() - 1 ? items.get(index + 1).getBrochureItemId() : null,
                index + 1, items.size());
    }

    /** Guarda las fotos y los textos de la prenda. Lanza {@link BuilderException} si algo no cumple. */
    public void saveItem(UUID brochureId, UUID itemId, ItemForm form) {
        Brochure b = find(brochureId);
        BrochureItem item = b.getItems().stream().filter(i -> i.getBrochureItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BuilderException(null, "brochures.notfound"));
        Product p = item.getProduct();

        Map<String, ProductImage> byId = new LinkedHashMap<>();
        for (ProductImage image : p.getProductImages()) {
            byId.put(image.getProductImageId().toString(), image);
        }

        ProductImage main = null;
        List<ProductImage> extras = new ArrayList<>();
        int mains = 0;
        for (Map.Entry<String, ProductImage> e : byId.entrySet()) {
            String role = form.getRoles().getOrDefault(e.getKey(), ROLE_NONE);
            if (ROLE_MAIN.equals(role)) {
                main = e.getValue();
                mains++;
            } else if (ROLE_EXTRA.equals(role)) {
                extras.add(e.getValue());
            }
        }
        if (!byId.isEmpty() && mains == 0) {
            throw new BuilderException("photos", "brochures.item.photos.main");
        }
        if (mains > 1) {
            throw new BuilderException("photos", "brochures.item.photos.mainmany");
        }
        if (extras.size() > MAX_EXTRAS) {
            throw new BuilderException("photos", "brochures.item.photos.extras");
        }

        if (form.getHighlightText() != null && form.getHighlightText().length() > MAX_HIGHLIGHT) {
            throw new BuilderException("highlightText", "brochures.validation.highlight.max");
        }
        if (form.getDetailsText() != null && form.getDetailsText().length() > MAX_DETAILS) {
            throw new BuilderException("detailsText", "brochures.validation.details.max");
        }

        item.setMainImage(main);
        item.setExtraImage1(extras.size() > 0 ? extras.get(0) : null);
        item.setExtraImage2(extras.size() > 1 ? extras.get(1) : null);
        item.setHighlightText(blankToNull(form.getHighlightText()));
        item.setDetailsText(blankToNull(form.getDetailsText()));
        b.setUpdatedAt(now());
    }

    /** El catálogo con todo lo necesario para el PDF. Precio y disponibilidad salen del producto, siempre al día. */
    @Transactional(readOnly = true)
    public PdfCatalog pdfData(UUID id) {
        Brochure b = find(id);
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        List<PdfItem> items = new ArrayList<>();
        for (BrochureItem item : b.getItems()) {
            Product p = item.getProduct();
            items.add(new PdfItem(p.getCode(), p.getName(), p.getCategory() == null ? null : p.getCategory().getName(),
                    p.getAvailability(), p.isNewOn(today), p.getGeneralPrice(), p.getFinalPrice(),
                    p.hasDiscount() ? p.getDiscountPercent().intValue() : 0,
                    item.getMainImage() == null ? null : storage.getPublicUrl(item.getMainImage().getStoragePath()),
                    item.extras().stream().map(i -> storage.getPublicUrl(i.getStoragePath())).toList(),
                    lines(item.getHighlightText()), lines(item.getDetailsText())));
        }
        return new PdfCatalog(b.getName(), b.getIntroText(), b.isShowPrices(), items);
    }

    /** Un texto en líneas (para ponerlo una debajo de otra en el PDF); sin líneas vacías. */
    static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        if (text != null) {
            for (String line : text.split("\r?\n")) {
                if (!line.isBlank()) {
                    out.add(line.trim());
                }
            }
        }
        return out;
    }

    /** Vuelve a poner el texto de partida del producto ("highlight" o "details") sin guardar nada todavía. */
    @Transactional(readOnly = true)
    public void resetText(UUID brochureId, UUID itemId, ItemForm form, String which) {
        Brochure b = find(brochureId);
        BrochureItem item = b.getItems().stream().filter(i -> i.getBrochureItemId().equals(itemId)).findFirst()
                .orElseThrow(() -> new BuilderException(null, "brochures.notfound"));
        if ("highlight".equals(which)) {
            form.setHighlightText(defaultHighlight(item.getProduct()));
        } else if ("details".equals(which)) {
            form.setDetailsText(defaultDetails(item.getProduct()));
        }
    }

    // ================================================================ textos de partida

    /** Frase destacada de partida: la descripción del producto. */
    public static String defaultHighlight(Product p) {
        return p.getDescription() == null ? "" : p.getDescription().trim();
    }

    /** Ficha de partida: material y demás detalles, colores y tallas del producto. */
    public static String defaultDetails(Product p) {
        List<String> lines = new ArrayList<>();
        for (ProductSpecification spec : p.getProductSpecifications()) {
            lines.add(spec.getName() + ": " + spec.getValue());
        }
        List<String> colors = p.getProductColors().stream()
                .sorted(Comparator.comparing((ProductColor c) -> !c.isPrimary()))
                .map(c -> c.getColor().getName()).toList();
        if (!colors.isEmpty()) {
            lines.add((colors.size() == 1 ? "Color: " : "Colores: ") + naturalList(colors));
        }
        List<String> sizes = p.getProductSizes().stream()
                .sorted(Comparator.comparing((ProductSize s) -> s.getSize().getSortOrder() == null ? 0 : s.getSize().getSortOrder()))
                .map(s -> s.getSize().getName()).toList();
        if (!sizes.isEmpty()) {
            lines.add("Tallas: " + naturalList(sizes));
        }
        if (Product.ON_REQUEST.equals(p.getAvailability())) {
            lines.add("Disponible bajo pedido.");
        }
        return String.join("\n", lines);
    }

    /** "S", "S y M", "S, M y L". */
    static String naturalList(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    // ================================================================ utilidades

    private Brochure find(UUID id) {
        return brochures.findById(id).orElseThrow(() -> new BuilderException(null, "brochures.notfound"));
    }

    private static Set<UUID> productIdsIn(Brochure b) {
        Set<UUID> ids = new HashSet<>();
        b.getItems().forEach(i -> ids.add(i.getProduct().getProductId()));
        return ids;
    }

    private static int indexOf(List<BrochureItem> items, UUID itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getBrochureItemId().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, String> rolesOf(BrochureItem item) {
        Map<String, String> roles = new LinkedHashMap<>();
        for (ProductImage image : item.getProduct().getProductImages()) {
            roles.put(image.getProductImageId().toString(), ROLE_NONE);
        }
        if (item.getMainImage() != null) {
            roles.put(item.getMainImage().getProductImageId().toString(), ROLE_MAIN);
        }
        for (ProductImage extra : item.extras()) {
            roles.put(extra.getProductImageId().toString(), ROLE_EXTRA);
        }
        return roles;
    }

    /** Miniatura de la foto principal elegida (o la del producto si aún no hay). */
    private String thumbOf(BrochureItem item) {
        if (item.getMainImage() != null) {
            return storage.getThumbPublicUrl(item.getMainImage().getStoragePath());
        }
        return productThumb(item.getProduct());
    }

    private String productThumb(Product p) {
        if (p.getPrimaryImagePath() != null && !p.getPrimaryImagePath().isBlank()) {
            return storage.getThumbPublicUrl(p.getPrimaryImagePath());
        }
        return p.getProductImages().isEmpty() ? null : storage.getThumbPublicUrl(p.getProductImages().get(0).getStoragePath());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
