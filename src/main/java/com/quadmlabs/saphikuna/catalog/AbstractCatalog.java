package com.quadmlabs.saphikuna.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lógica común de los catálogos: validar nombre, evitar duplicados y no borrar lo que está en uso. */
public abstract class AbstractCatalog<T> implements CatalogAdapter {

    public static final int NAME_MAX = 60;

    protected abstract JpaRepository<T, UUID> repository();

    protected abstract UUID idOf(T entity);

    protected abstract String nameOf(T entity);

    protected abstract Map<String, String> valuesOf(T entity);

    /** Cantidad de productos que usan cada elemento (id → cantidad). */
    protected abstract Map<UUID, Long> usage();

    protected abstract T create(UUID id, OffsetDateTime now);

    protected abstract void apply(T entity, Map<String, String> values);

    /** Validaciones propias de cada catálogo (campo → clave de mensaje). */
    protected Map<String, String> validate(Map<String, String> values) {
        return new LinkedHashMap<>();
    }

    protected Comparator<T> order() {
        return Comparator.comparing(e -> normalize(nameOf(e)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItem> list(String q) {

        Map<UUID, Long> usage = usage();
        String needle = q == null ? "" : normalize(q);

        List<CatalogItem> items = new ArrayList<>();
        repository().findAll().stream().sorted(order()).forEach(e -> {
            CatalogItem item = toItem(e, usage);
            if (needle.isEmpty() || matches(item, needle)) {
                items.add(item);
            }
        });
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogItem> find(UUID id) {
        Map<UUID, Long> usage = usage();
        return repository().findById(id).map(e -> toItem(e, usage));
    }

    @Override
    public Map<String, String> defaults() {
        return new LinkedHashMap<>();
    }

    @Override
    @Transactional
    public Map<String, String> save(UUID id, Map<String, String> submitted) {

        Map<String, String> values = new LinkedHashMap<>();
        fields().forEach(f -> {
            String v = submitted == null ? null : submitted.get(f.name());
            values.put(f.name(), v == null ? "" : v.trim());
        });

        Map<String, String> errors = new LinkedHashMap<>();

        String name = values.get("name");
        if (name.isEmpty()) {
            errors.put("name", "catalog.validation.name.required");
        } else if (name.length() > NAME_MAX) {
            errors.put("name", "catalog.validation.name.max");
        } else if (nameTaken(name, id)) {
            errors.put("name", "catalog.validation.name.duplicate");
        }

        validate(values).forEach(errors::putIfAbsent);

        if (!errors.isEmpty()) {
            return errors;
        }

        T entity = id == null
                ? create(UUID.randomUUID(), OffsetDateTime.now())
                : repository().findById(id).orElseThrow(() -> new IllegalArgumentException("No encontrado"));

        apply(entity, values);
        repository().save(entity);
        return errors;
    }

    @Override
    @Transactional
    public DeleteResult delete(UUID id) {

        T entity = repository().findById(id).orElseThrow(() -> new IllegalArgumentException("No encontrado"));
        long used = usage().getOrDefault(id, 0L);

        if (used > 0) {
            return new DeleteResult(false, nameOf(entity), used);
        }

        repository().delete(entity);
        return new DeleteResult(true, nameOf(entity), 0);
    }

    // ------------------------------------------------------------------

    private CatalogItem toItem(T e, Map<UUID, Long> usage) {
        return new CatalogItem(idOf(e), nameOf(e), valuesOf(e), usage.getOrDefault(idOf(e), 0L));
    }

    private boolean matches(CatalogItem item, String needle) {
        if (normalize(item.name()).contains(needle)) {
            return true;
        }
        return item.values().values().stream().anyMatch(v -> v != null && normalize(v).contains(needle));
    }

    private boolean nameTaken(String name, UUID exceptId) {
        String wanted = normalize(name);
        return repository().findAll().stream()
                .filter(e -> exceptId == null || !exceptId.equals(idOf(e)))
                .anyMatch(e -> normalize(nameOf(e)).equals(wanted));
    }

    /** Sin tildes ni mayúsculas: "Café" y "cafe" son el mismo nombre. */
    public static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    protected static Map<UUID, Long> toMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }
}
