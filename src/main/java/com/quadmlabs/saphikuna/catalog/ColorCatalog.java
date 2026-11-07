package com.quadmlabs.saphikuna.catalog;

import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductColorRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ColorCatalog extends AbstractCatalog<Color> {

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final ColorRepository colors;
    private final ProductColorRepository productColors;

    public ColorCatalog(ColorRepository colors, ProductColorRepository productColors) {
        this.colors = colors;
        this.productColors = productColors;
    }

    @Override
    public String key() {
        return "colors";
    }

    @Override
    public List<CatalogField> fields() {
        return List.of(
                new CatalogField("name", "catalog.field.name", CatalogField.Type.TEXT, true, NAME_MAX),
                new CatalogField("hex", "catalog.field.hex", CatalogField.Type.COLOR, true, 7));
    }

    @Override
    public Map<String, String> defaults() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("hex", "#808080");
        return d;
    }

    @Override
    protected JpaRepository<Color, UUID> repository() {
        return colors;
    }

    @Override
    protected UUID idOf(Color c) {
        return c.getColorId();
    }

    @Override
    protected String nameOf(Color c) {
        return c.getName();
    }

    @Override
    protected Map<String, String> valuesOf(Color c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("name", c.getName());
        // Un color viejo sin hex se muestra gris para que el selector tenga un valor válido
        v.put("hex", c.getHexCode() != null && HEX.matcher(c.getHexCode()).matches()
                ? c.getHexCode().toUpperCase() : "#808080");
        return v;
    }

    @Override
    protected Map<UUID, Long> usage() {
        return toMap(productColors.countByColor());
    }

    @Override
    protected Color create(UUID id, OffsetDateTime now) {
        Color c = new Color();
        c.setColorId(id);
        c.setCreatedAt(now);
        return c;
    }

    @Override
    protected void apply(Color c, Map<String, String> v) {
        c.setName(v.get("name"));
        c.setHexCode(v.get("hex").toUpperCase());
    }

    @Override
    protected Map<String, String> validate(Map<String, String> v) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!HEX.matcher(v.get("hex")).matches()) {
            errors.put("hex", "catalog.validation.hex.invalid");
        }
        return errors;
    }
}
