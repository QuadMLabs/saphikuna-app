package com.quadmlabs.saphikuna.catalog;

import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.repository.ProductSizeRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SizeCatalog extends AbstractCatalog<Size> {

    private static final int NAME_MAX_SIZE = 20;
    private static final int ORDER_MAX = 999;

    private final SizeRepository sizes;
    private final ProductSizeRepository productSizes;

    public SizeCatalog(SizeRepository sizes, ProductSizeRepository productSizes) {
        this.sizes = sizes;
        this.productSizes = productSizes;
    }

    @Override
    public String key() {
        return "sizes";
    }

    @Override
    public List<CatalogField> fields() {
        return List.of(
                new CatalogField("name", "catalog.field.name", CatalogField.Type.TEXT, true, NAME_MAX_SIZE),
                new CatalogField("sortOrder", "catalog.field.sortOrder", CatalogField.Type.NUMBER, true, 3));
    }

    /** El orden sugerido para una talla nueva es el siguiente al último. */
    @Override
    public Map<String, String> defaults() {
        int next = sizes.findAll().stream()
                .mapToInt(s -> s.getSortOrder() == null ? 0 : s.getSortOrder())
                .max().orElse(0) + 1;
        Map<String, String> d = new LinkedHashMap<>();
        d.put("sortOrder", String.valueOf(next));
        return d;
    }

    /** Las tallas se muestran por su orden (XS, S, M, L...), no alfabéticamente. */
    @Override
    protected Comparator<Size> order() {
        return Comparator.comparing((Size s) -> s.getSortOrder() == null ? 0 : s.getSortOrder())
                .thenComparing(s -> normalize(s.getName()));
    }

    @Override
    protected JpaRepository<Size, UUID> repository() {
        return sizes;
    }

    @Override
    protected UUID idOf(Size s) {
        return s.getSizeId();
    }

    @Override
    protected String nameOf(Size s) {
        return s.getName();
    }

    @Override
    protected Map<String, String> valuesOf(Size s) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("name", s.getName());
        v.put("sortOrder", s.getSortOrder() == null ? "0" : String.valueOf(s.getSortOrder()));
        return v;
    }

    @Override
    protected Map<UUID, Long> usage() {
        return toMap(productSizes.countBySize());
    }

    @Override
    protected Size create(UUID id, OffsetDateTime now) {
        Size s = new Size();
        s.setSizeId(id);
        s.setCreatedAt(now);
        return s;
    }

    @Override
    protected void apply(Size s, Map<String, String> v) {
        s.setName(v.get("name"));
        s.setSortOrder(Short.valueOf(v.get("sortOrder")));
    }

    @Override
    protected Map<String, String> validate(Map<String, String> v) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (v.get("name").length() > NAME_MAX_SIZE) {
            errors.put("name", "catalog.validation.size.name.max");
        }
        try {
            int order = Integer.parseInt(v.get("sortOrder"));
            if (order < 0 || order > ORDER_MAX) {
                errors.put("sortOrder", "catalog.validation.order.invalid");
            }
        } catch (NumberFormatException e) {
            errors.put("sortOrder", "catalog.validation.order.invalid");
        }
        return errors;
    }
}
