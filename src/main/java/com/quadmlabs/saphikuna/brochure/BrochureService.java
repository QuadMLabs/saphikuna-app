package com.quadmlabs.saphikuna.brochure;

import com.quadmlabs.saphikuna.brochure.BrochureData.ColorChip;
import com.quadmlabs.saphikuna.brochure.BrochureData.Item;
import com.quadmlabs.saphikuna.brochure.BrochureData.Section;
import com.quadmlabs.saphikuna.brochure.BrochureData.SizePrice;
import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductCollection;
import com.quadmlabs.saphikuna.entity.ProductColor;
import com.quadmlabs.saphikuna.entity.ProductImage;
import com.quadmlabs.saphikuna.entity.ProductSize;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.service.SettingsService;
import com.quadmlabs.saphikuna.service.StorageService;

import jakarta.persistence.criteria.Predicate;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma el catálogo: elige los productos según las opciones y los deja listos para mostrar.
 * Solo entran productos visibles (los ocultos nunca salen en un catálogo público).
 */
@Service
public class BrochureService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CollectionRepository collections;
    private final SettingsService settings;
    private final StorageService storage;
    private final MessageSource messages;
    private final Clock clock;

    public BrochureService(
            ProductRepository products,
            CategoryRepository categories,
            CollectionRepository collections,
            SettingsService settings,
            StorageService storage,
            MessageSource messages,
            Clock clock) {

        this.products = products;
        this.categories = categories;
        this.collections = collections;
        this.settings = settings;
        this.storage = storage;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BrochureData build(BrochureOptions options) {

        LocalDate today = LocalDate.now(clock);
        StoreInfoForm store = settings.storeInfo();

        List<Product> selected = products.findAll(spec(options, today), Sort.by("code"));
        List<Item> items = selected.stream().map(p -> toItem(p, options, store, today)).toList();

        return new BrochureData(
                store,
                options.titleOrDefault(),
                scope(options),
                today,
                sections(items, options),
                items.size(),
                options);
    }

    // ------------------------------------------------------------------
    // Selección
    // ------------------------------------------------------------------

    private Specification<Product> spec(BrochureOptions o, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();

            where.add(cb.equal(root.get("status"), Product.ACTIVE));

            if (!o.isSoldout()) {
                where.add(cb.notEqual(root.get("availability"), Product.SOLD_OUT));
            }
            if (o.categoryId() != null) {
                where.add(cb.equal(root.get("category").get("categoryId"), o.categoryId()));
            }
            if (o.collectionId() != null) {
                var sub = query.subquery(UUID.class);
                var pc = sub.from(ProductCollection.class);
                sub.select(pc.get("product").<UUID>get("productId"))
                        .where(cb.equal(pc.get("collection").get("collectionId"), o.collectionId()));
                where.add(root.<UUID>get("productId").in(sub));
            }
            if (o.isNews()) {
                where.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("newUntil"), today));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /** "Navidad 2026", "Chompas"... lo que abarca el catálogo; vacío si es todo. */
    private String scope(BrochureOptions o) {
        List<String> parts = new ArrayList<>();
        if (o.categoryId() != null) {
            categories.findById(o.categoryId()).ifPresent(c -> parts.add(c.getName()));
        }
        if (o.collectionId() != null) {
            collections.findById(o.collectionId()).ifPresent(c -> parts.add(c.getName()));
        }
        if (o.isNews()) {
            parts.add(text("brochure.scope.news"));
        }
        return String.join(" · ", parts);
    }

    // ------------------------------------------------------------------
    // Secciones
    // ------------------------------------------------------------------

    private List<Section> sections(List<Item> items, BrochureOptions options) {

        if (items.isEmpty()) {
            return List.of();
        }

        // Si se pidió una sola categoría o no se quiere agrupar: una lista sin encabezados
        if (!options.isGroup() || options.categoryId() != null) {
            return List.of(new Section(null, false, items));
        }

        List<Section> sections = new ArrayList<>();

        // Novedades primero (cada producto sale una sola vez)
        List<Item> news = items.stream().filter(Item::isNew).toList();
        if (!news.isEmpty()) {
            sections.add(new Section(text("brochure.section.new"), true, news));
        }

        Map<String, List<Item>> byCategory = new LinkedHashMap<>();
        items.stream().filter(i -> !i.isNew())
                // por categoría (las "sin categoría" al final) y, dentro de cada una, por código
                .sorted(Comparator.comparing(Item::categoryName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Item::code, Comparator.nullsLast(String::compareTo)))
                .forEach(i -> byCategory.computeIfAbsent(
                        i.categoryName() == null ? text("brochure.section.other") : i.categoryName(),
                        k -> new ArrayList<>()).add(i));

        byCategory.forEach((name, list) -> sections.add(new Section(name, false, list)));
        return sections;
    }

    // ------------------------------------------------------------------
    // Producto -> dato listo para mostrar
    // ------------------------------------------------------------------

    private Item toItem(Product p, BrochureOptions o, StoreInfoForm store, LocalDate today) {

        List<String> photos = new ArrayList<>();
        // la principal primero, luego el resto en su orden
        String primary = p.getPrimaryImagePath();
        if (primary != null && !primary.isBlank()) {
            photos.add(storage.getPublicUrl(primary));
        }
        for (ProductImage image : p.getProductImages()) {
            if (photos.size() >= o.photoCount()) {
                break;
            }
            String url = storage.getPublicUrl(image.getStoragePath());
            if (!photos.contains(url)) {
                photos.add(url);
            }
        }
        if (photos.size() > o.photoCount()) {
            photos = new ArrayList<>(photos.subList(0, o.photoCount()));
        }

        List<String> sizes = p.getProductSizes().stream()
                .sorted(Comparator.comparing((ProductSize s) -> s.getSize().getSortOrder() == null ? 0 : s.getSize().getSortOrder()))
                .map(s -> s.getSize().getName())
                .toList();

        List<SizePrice> special = p.getProductSizes().stream()
                .filter(s -> s.getPrice() != null)
                .sorted(Comparator.comparing((ProductSize s) -> s.getSize().getSortOrder() == null ? 0 : s.getSize().getSortOrder()))
                .map(s -> new SizePrice(s.getSize().getName(), s.getPrice()))
                .toList();

        List<ColorChip> colors = p.getProductColors().stream()
                .sorted(Comparator.comparing((ProductColor c) -> !c.isPrimary()))
                .map(c -> new ColorChip(c.getColor().getName(), c.getColor().getHexCode(), c.isPrimary()))
                .toList();

        String availability = switch (p.getAvailability() == null ? "" : p.getAvailability()) {
            case Product.ON_REQUEST -> "onRequest";
            case Product.SOLD_OUT -> "soldOut";
            default -> "available";
        };

        return new Item(
                p.getProductId(),
                p.getCode(),
                p.getName(),
                p.getDescription() == null || p.getDescription().isBlank() ? null : p.getDescription().trim(),
                p.getCategory() == null ? null : p.getCategory().getName(),
                p.getGeneralPrice(),
                p.getFinalPrice(),
                p.hasDiscount() ? p.getDiscountPercent().intValue() : 0,
                p.isNewOn(today),
                availability,
                sizes,
                colors,
                special,
                photos,
                whatsappUrl(store, p));
    }

    /** Enlace "Consultar por WhatsApp" con el producto ya escrito; null si no hay número. */
    private String whatsappUrl(StoreInfoForm store, Product p) {
        String digits = store.getWhatsapp() == null ? "" : store.getWhatsapp().replaceAll("[^0-9]", "");
        if (digits.length() < 8) {
            return null;
        }
        String message = text("brochure.whatsapp.message", p.getName(), p.getCode() == null ? "" : p.getCode());
        return "https://wa.me/" + digits + "?text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
