package com.quadmlabs.saphikuna.catalog;

import com.quadmlabs.saphikuna.entity.Collection;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.repository.ProductCollectionRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CollectionCatalog extends AbstractCatalog<Collection> {

    static final int DESCRIPTION_MAX = 300;

    private final CollectionRepository collections;
    private final ProductCollectionRepository productCollections;

    public CollectionCatalog(CollectionRepository collections, ProductCollectionRepository productCollections) {
        this.collections = collections;
        this.productCollections = productCollections;
    }

    @Override
    public String key() {
        return "collections";
    }

    @Override
    public List<CatalogField> fields() {
        return List.of(
                new CatalogField("name", "catalog.field.name", CatalogField.Type.TEXT, true, NAME_MAX),
                new CatalogField("description", "catalog.field.description", CatalogField.Type.TEXTAREA, false,
                        DESCRIPTION_MAX));
    }

    @Override
    protected JpaRepository<Collection, UUID> repository() {
        return collections;
    }

    @Override
    protected UUID idOf(Collection c) {
        return c.getCollectionId();
    }

    @Override
    protected String nameOf(Collection c) {
        return c.getName();
    }

    @Override
    protected Map<String, String> valuesOf(Collection c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("name", c.getName());
        v.put("description", c.getDescription() == null ? "" : c.getDescription());
        return v;
    }

    @Override
    protected Map<UUID, Long> usage() {
        return toMap(productCollections.countByCollection());
    }

    @Override
    protected Collection create(UUID id, OffsetDateTime now) {
        Collection c = new Collection();
        c.setCollectionId(id);
        c.setCreatedAt(now);
        return c;
    }

    @Override
    protected void apply(Collection c, Map<String, String> v) {
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
