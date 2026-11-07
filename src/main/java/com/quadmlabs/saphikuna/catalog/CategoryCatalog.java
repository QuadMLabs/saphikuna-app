package com.quadmlabs.saphikuna.catalog;

import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CategoryCatalog extends AbstractCatalog<Category> {

    static final int DESCRIPTION_MAX = 300;

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryCatalog(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Override
    public String key() {
        return "categories";
    }

    @Override
    public List<CatalogField> fields() {
        return List.of(
                new CatalogField("name", "catalog.field.name", CatalogField.Type.TEXT, true, NAME_MAX),
                new CatalogField("description", "catalog.field.description", CatalogField.Type.TEXTAREA, false,
                        DESCRIPTION_MAX));
    }

    @Override
    protected JpaRepository<Category, UUID> repository() {
        return categories;
    }

    @Override
    protected UUID idOf(Category c) {
        return c.getCategoryId();
    }

    @Override
    protected String nameOf(Category c) {
        return c.getName();
    }

    @Override
    protected Map<String, String> valuesOf(Category c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("name", c.getName());
        v.put("description", c.getDescription() == null ? "" : c.getDescription());
        return v;
    }

    @Override
    protected Map<UUID, Long> usage() {
        return toMap(products.countByCategory());
    }

    @Override
    protected Category create(UUID id, OffsetDateTime now) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setCreatedAt(now);
        return c;
    }

    @Override
    protected void apply(Category c, Map<String, String> v) {
        c.setName(v.get("name"));
        c.setDescription(v.get("description").isEmpty() ? null : v.get("description"));
    }

    @Override
    protected Map<String, String> validate(Map<String, String> v) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (v.get("description").length() > DESCRIPTION_MAX) {
            errors.put("description", "catalog.validation.description.max");
        }
        return errors;
    }
}
