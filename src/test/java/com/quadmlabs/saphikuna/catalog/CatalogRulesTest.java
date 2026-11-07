package com.quadmlabs.saphikuna.catalog;

import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductColorRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.repository.ProductSizeRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de los catálogos: nombre obligatorio y único, validaciones y no borrar lo que está en uso. */
class CatalogRulesTest {

    private ColorRepository colorRepo;
    private ProductColorRepository productColorRepo;
    private ColorCatalog colors;
    private final List<Color> storedColors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        colorRepo = mock(ColorRepository.class);
        productColorRepo = mock(ProductColorRepository.class);
        when(colorRepo.findAll()).thenAnswer(inv -> new ArrayList<>(storedColors));
        when(colorRepo.findById(any())).thenAnswer(inv -> storedColors.stream()
                .filter(c -> c.getColorId().equals(inv.getArgument(0))).findFirst());
        when(productColorRepo.countByColor()).thenReturn(List.of());
        colors = new ColorCatalog(colorRepo, productColorRepo);
    }

    private Color color(String name, String hex) {
        Color c = new Color();
        c.setColorId(UUID.randomUUID());
        c.setName(name);
        c.setHexCode(hex);
        storedColors.add(c);
        return c;
    }

    // ---------------- Colores ----------------

    @Test
    void newColorIsSavedWithUppercaseHexAndTrimmedName() {
        Map<String, String> errors = colors.save(null, Map.of("name", "  Verde agua ", "hex", "#3cb371"));

        assertTrue(errors.isEmpty());
        ArgumentCaptor<Color> saved = ArgumentCaptor.forClass(Color.class);
        verify(colorRepo).save(saved.capture());
        assertEquals("Verde agua", saved.getValue().getName());
        assertEquals("#3CB371", saved.getValue().getHexCode());
        assertTrue(saved.getValue().getColorId() != null && saved.getValue().getCreatedAt() != null);
    }

    @Test
    void nameIsRequired() {
        Map<String, String> errors = colors.save(null, Map.of("name", "   ", "hex", "#112233"));

        assertEquals("catalog.validation.name.required", errors.get("name"));
        verify(colorRepo, never()).save(any());
    }

    @Test
    void invalidHexIsRejected() {
        for (String hex : new String[] { "", "rojo", "#12345", "#GGGGGG", "112233" }) {
            Map<String, String> errors = colors.save(null, Map.of("name", "Prueba", "hex", hex));
            assertEquals("catalog.validation.hex.invalid", errors.get("hex"), "hex: '" + hex + "'");
        }
        verify(colorRepo, never()).save(any());
    }

    @Test
    void duplicateNameIgnoresAccentsAndCase() {
        color("Café", "#6F4E37");

        Map<String, String> errors = colors.save(null, Map.of("name", "CAFE", "hex", "#000000"));

        assertEquals("catalog.validation.name.duplicate", errors.get("name"));
    }

    @Test
    void editingKeepsItsOwnNameWithoutCountingAsDuplicate() {
        Color cafe = color("Café", "#6F4E37");

        Map<String, String> errors = colors.save(cafe.getColorId(), Map.of("name", "Café", "hex", "#7a5a40"));

        assertTrue(errors.isEmpty());
        assertEquals("#7A5A40", cafe.getHexCode());
    }

    @Test
    void colorInUseCannotBeDeleted() {
        Color rojo = color("Rojo", "#D32F2F");
        when(productColorRepo.countByColor()).thenReturn(List.<Object[]>of(new Object[] { rojo.getColorId(), 3L }));

        CatalogAdapter.DeleteResult result = colors.delete(rojo.getColorId());

        assertFalse(result.deleted());
        assertEquals(3, result.usage());
        assertEquals("Rojo", result.name());
        verify(colorRepo, never()).delete(any());
    }

    @Test
    void unusedColorIsDeleted() {
        Color vino = color("Vino", "#722F37");

        CatalogAdapter.DeleteResult result = colors.delete(vino.getColorId());

        assertTrue(result.deleted());
        verify(colorRepo).delete(vino);
    }

    @Test
    void listShowsUsageAndFiltersBySearchText() {
        Color rojo = color("Rojo", "#D32F2F");
        color("Azul rey", "#4169E1");
        when(productColorRepo.countByColor()).thenReturn(List.<Object[]>of(new Object[] { rojo.getColorId(), 2L }));

        List<CatalogItem> all = colors.list(null);
        assertEquals(List.of("Azul rey", "Rojo"), all.stream().map(CatalogItem::name).toList());
        assertEquals(2, all.get(1).usage());

        assertEquals(List.of("Rojo"), colors.list("ROJ").stream().map(CatalogItem::name).toList());
        assertEquals(List.of("Azul rey"), colors.list("#4169").stream().map(CatalogItem::name).toList());
    }

    @Test
    void colorWithoutHexShowsGrayInTheEditor() {
        Color old = color("Rubi", null);

        assertEquals("#808080", colors.find(old.getColorId()).orElseThrow().values().get("hex"));
    }

    // ---------------- Tallas ----------------

    private SizeCatalog sizes(List<Size> stored) {
        SizeRepository repo = mock(SizeRepository.class);
        when(repo.findAll()).thenReturn(stored);
        ProductSizeRepository usage = mock(ProductSizeRepository.class);
        when(usage.countBySize()).thenReturn(List.of());
        return new SizeCatalog(repo, usage);
    }

    private Size size(String name, int order) {
        Size s = new Size();
        s.setSizeId(UUID.randomUUID());
        s.setName(name);
        s.setSortOrder((short) order);
        return s;
    }

    @Test
    void sizesAreListedByOrderNotAlphabetically() {
        SizeCatalog catalog = sizes(List.of(size("XL", 4), size("S", 2), size("M", 3), size("XS", 1)));

        assertEquals(List.of("XS", "S", "M", "XL"), catalog.list(null).stream().map(CatalogItem::name).toList());
    }

    @Test
    void newSizeSuggestsTheNextOrder() {
        SizeCatalog catalog = sizes(List.of(size("S", 2), size("L", 4)));

        assertEquals("5", catalog.defaults().get("sortOrder"));
    }

    @Test
    void sizeOrderMustBeANumberBetween0And999() {
        SizeCatalog catalog = sizes(List.of());

        for (String bad : new String[] { "", "abc", "-1", "1000", "2.5" }) {
            assertEquals("catalog.validation.order.invalid",
                    catalog.save(null, Map.of("name", "XXL", "sortOrder", bad)).get("sortOrder"), "orden: '" + bad + "'");
        }
        assertTrue(catalog.save(null, Map.of("name", "XXL", "sortOrder", "6")).isEmpty());
    }

    // ---------------- Categorías ----------------

    @Test
    void emptyDescriptionIsStoredAsNullAndLongOneIsRejected() {
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.findById(any())).thenReturn(Optional.empty());
        ProductRepository products = mock(ProductRepository.class);
        when(products.countByCategory()).thenReturn(List.of());
        CategoryCatalog catalog = new CategoryCatalog(repo, products);

        assertTrue(catalog.save(null, Map.of("name", "Ponchos", "description", "")).isEmpty());
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(repo).save(saved.capture());
        assertNull(saved.getValue().getDescription());

        Map<String, String> errors = catalog.save(null, Map.of("name", "Otra", "description", "x".repeat(301)));
        assertEquals("catalog.validation.description.max", errors.get("description"));
    }
}
