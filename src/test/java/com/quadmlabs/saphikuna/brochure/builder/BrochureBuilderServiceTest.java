package com.quadmlabs.saphikuna.brochure.builder;

import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.BuilderException;
import com.quadmlabs.saphikuna.entity.Brochure;
import com.quadmlabs.saphikuna.entity.BrochureItem;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductColor;
import com.quadmlabs.saphikuna.entity.ProductImage;
import com.quadmlabs.saphikuna.entity.ProductSize;
import com.quadmlabs.saphikuna.entity.ProductSpecification;
import com.quadmlabs.saphikuna.entity.Size;
import com.quadmlabs.saphikuna.repository.BrochureRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reglas del armado: fotos principal/apoyo, textos de partida, orden y copia. */
class BrochureBuilderServiceTest {

    private BrochureRepository brochures;
    private ProductRepository products;
    private BrochureBuilderService service;
    private Brochure brochure;

    @BeforeEach
    void setUp() {
        brochures = mock(BrochureRepository.class);
        products = mock(ProductRepository.class);
        StorageService storage = mock(StorageService.class);
        when(storage.getThumbPublicUrl(any())).thenReturn("http://thumb");
        service = new BrochureBuilderService(brochures, products, mock(ProductService.class), storage,
                Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC));

        brochure = new Brochure();
        brochure.setBrochureId(UUID.randomUUID());
        brochure.setName("Verano");
        when(brochures.findById(brochure.getBrochureId())).thenReturn(Optional.of(brochure));
        when(brochures.save(any(Brochure.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------------------------------------------------------------- datos de prueba

    private static ProductImage image(Product p, String path, int order) {
        ProductImage i = new ProductImage();
        i.setProductImageId(UUID.randomUUID());
        i.setProduct(p);
        i.setStoragePath(path);
        i.setSortOrder((short) order);
        return i;
    }

    private static Product product(String code, int photos) {
        Product p = new Product();
        p.setProductId(UUID.randomUUID());
        p.setCode(code);
        p.setName("Prenda " + code);
        p.setDescription("  Un suéter clásico.  ");
        p.setStatus(Product.ACTIVE);
        p.setAvailability(Product.AVAILABLE);
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < photos; i++) {
            images.add(image(p, "products/" + code + "/" + i + ".webp", i));
        }
        p.setProductImages(images);
        if (!images.isEmpty()) {
            // la principal del producto es la segunda foto: no siempre es la primera
            p.setPrimaryImagePath(images.get(Math.min(1, images.size() - 1)).getStoragePath());
        }
        return p;
    }

    private BrochureItem itemFor(Product p, int position) {
        BrochureItem item = new BrochureItem();
        item.setBrochureItemId(UUID.randomUUID());
        item.setBrochure(brochure);
        item.setProduct(p);
        item.setPosition(position);
        item.setMainImage(p.getProductImages().isEmpty() ? null : p.getProductImages().get(0));
        brochure.getItems().add(item);
        return item;
    }

    private static ItemForm roles(Product p, String... roleByIndex) {
        ItemForm form = new ItemForm();
        for (int i = 0; i < p.getProductImages().size(); i++) {
            form.getRoles().put(p.getProductImages().get(i).getProductImageId().toString(),
                    i < roleByIndex.length ? roleByIndex[i] : "none");
        }
        return form;
    }

    // ---------------------------------------------------------------- textos de partida

    @Test
    void naturalListReadsLikeAPerson() {
        assertThat(BrochureBuilderService.naturalList(List.of())).isEmpty();
        assertThat(BrochureBuilderService.naturalList(List.of("S"))).isEqualTo("S");
        assertThat(BrochureBuilderService.naturalList(List.of("S", "M"))).isEqualTo("S y M");
        assertThat(BrochureBuilderService.naturalList(List.of("S", "M", "L"))).isEqualTo("S, M y L");
    }

    @Test
    void defaultDetailsListsTheProductDataOneLineEach() {
        Product p = product("SPK-001", 0);

        ProductSpecification spec = new ProductSpecification();
        spec.setName("Material");
        spec.setValue("Hilo angora");
        p.setProductSpecifications(List.of(spec));

        Color white = new Color();
        white.setName("Blanco");
        Color black = new Color();
        black.setName("Negro");
        ProductColor secondary = new ProductColor();
        secondary.setColor(black);
        secondary.setPrimary(false);
        ProductColor primary = new ProductColor();
        primary.setColor(white);
        primary.setPrimary(true);
        p.setProductColors(List.of(secondary, primary)); // el principal va primero aunque llegue después

        Size m = new Size();
        m.setName("M");
        m.setSortOrder((short) 2);
        Size s = new Size();
        s.setName("S");
        s.setSortOrder((short) 1);
        ProductSize psM = new ProductSize();
        psM.setSize(m);
        ProductSize psS = new ProductSize();
        psS.setSize(s);
        p.setProductSizes(List.of(psM, psS));
        p.setAvailability(Product.ON_REQUEST);

        assertThat(BrochureBuilderService.defaultDetails(p)).isEqualTo(
                "Material: Hilo angora\nColores: Blanco y Negro\nTallas: S y M\nDisponible bajo pedido.");
    }

    @Test
    void defaultHighlightIsTheTrimmedDescription() {
        assertThat(BrochureBuilderService.defaultHighlight(product("SPK-001", 0))).isEqualTo("Un suéter clásico.");
    }

    // ---------------------------------------------------------------- agregar prendas

    @Test
    void addedProductsStartWithItsMainPhotoTwoSupportPhotosAndTheProductTexts() {
        Product p = product("SPK-001", 4);
        when(products.findAllById(any())).thenReturn(List.of(p));

        int added = service.addProducts(brochure.getBrochureId(), List.of(p.getProductId()));

        assertThat(added).isEqualTo(1);
        BrochureItem item = brochure.getItems().get(0);
        assertThat(item.getMainImage()).isSameAs(p.getProductImages().get(1));           // la principal del producto
        assertThat(item.extras()).containsExactly(p.getProductImages().get(0), p.getProductImages().get(2));
        assertThat(item.getHighlightText()).isEqualTo("Un suéter clásico.");
        assertThat(item.getPosition()).isZero();
    }

    @Test
    void productsAlreadyInTheCatalogOrHiddenAreNotAddedAgain() {
        Product inside = product("SPK-001", 1);
        Product hidden = product("SPK-002", 1);
        hidden.setStatus(Product.INACTIVE);
        Product fresh = product("SPK-003", 1);
        itemFor(inside, 0);
        when(products.findAllById(any())).thenReturn(List.of(inside, hidden, fresh));

        int added = service.addProducts(brochure.getBrochureId(), List.of(inside.getProductId(), hidden.getProductId(), fresh.getProductId()));

        assertThat(added).isEqualTo(1);
        assertThat(brochure.getItems()).extracting(i -> i.getProduct().getCode()).containsExactly("SPK-001", "SPK-003");
        assertThat(brochure.getItems().get(1).getPosition()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- guardar la ficha

    @Test
    void savesTheChosenPhotosAndTexts() {
        Product p = product("SPK-001", 4);
        BrochureItem item = itemFor(p, 0);

        ItemForm form = roles(p, "extra", "main", "none", "extra");
        form.setHighlightText("  Clásico y elegante.  ");
        form.setDetailsText("Punto pata de gallo.\nColores: blanco y negro.");
        service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), form);

        assertThat(item.getMainImage()).isSameAs(p.getProductImages().get(1));
        assertThat(item.extras()).containsExactly(p.getProductImages().get(0), p.getProductImages().get(3));
        assertThat(item.getHighlightText()).isEqualTo("Clásico y elegante.");
        assertThat(item.getDetailsText()).contains("Punto pata de gallo");
    }

    @Test
    void blankTextsAreStoredAsEmpty() {
        Product p = product("SPK-001", 1);
        BrochureItem item = itemFor(p, 0);
        ItemForm form = roles(p, "main");
        form.setHighlightText("   ");

        service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), form);

        assertThat(item.getHighlightText()).isNull();
    }

    @Test
    void aPrincipalPhotoIsRequiredWhenTheProductHasPhotos() {
        Product p = product("SPK-001", 3);
        BrochureItem item = itemFor(p, 0);

        assertThatThrownBy(() -> service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), roles(p, "extra", "extra")))
                .isInstanceOf(BuilderException.class).hasMessage("brochures.item.photos.main");
        assertThatThrownBy(() -> service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), roles(p, "main", "main")))
                .isInstanceOf(BuilderException.class).hasMessage("brochures.item.photos.mainmany");
    }

    @Test
    void atMostTwoSupportPhotos() {
        Product p = product("SPK-001", 4);
        BrochureItem item = itemFor(p, 0);

        assertThatThrownBy(() -> service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(),
                roles(p, "main", "extra", "extra", "extra")))
                .isInstanceOf(BuilderException.class).hasMessage("brochures.item.photos.extras");
    }

    @Test
    void aProductWithoutPhotosCanStillBeSaved() {
        Product p = product("SPK-001", 0);
        BrochureItem item = itemFor(p, 0);

        service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), new ItemForm());

        assertThat(item.getMainImage()).isNull();
    }

    @Test
    void textsThatAreTooLongAreRejected() {
        Product p = product("SPK-001", 1);
        BrochureItem item = itemFor(p, 0);
        ItemForm form = roles(p, "main");
        form.setHighlightText("x".repeat(BrochureBuilderService.MAX_HIGHLIGHT + 1));

        assertThatThrownBy(() -> service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), form))
                .isInstanceOf(BuilderException.class).hasMessage("brochures.validation.highlight.max");
    }

    @Test
    void photosOfAnotherProductAreIgnored() {
        Product p = product("SPK-001", 2);
        Product other = product("SPK-002", 1);
        BrochureItem item = itemFor(p, 0);
        ItemForm form = roles(p, "main");
        form.getRoles().put(other.getProductImages().get(0).getProductImageId().toString(), "extra");

        service.saveItem(brochure.getBrochureId(), item.getBrochureItemId(), form);

        assertThat(item.extras()).isEmpty();
    }

    // ---------------------------------------------------------------- orden, quitar, duplicar

    @Test
    void movingAnItemRenumbersThePositions() {
        BrochureItem a = itemFor(product("SPK-001", 1), 0);
        BrochureItem b = itemFor(product("SPK-002", 1), 1);
        BrochureItem c = itemFor(product("SPK-003", 1), 2);

        service.moveItem(brochure.getBrochureId(), c.getBrochureItemId(), -1);

        assertThat(brochure.getItems()).containsExactly(a, c, b);
        assertThat(brochure.getItems()).extracting(BrochureItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void movingPastTheEdgeDoesNothing() {
        BrochureItem a = itemFor(product("SPK-001", 1), 0);
        BrochureItem b = itemFor(product("SPK-002", 1), 1);

        service.moveItem(brochure.getBrochureId(), a.getBrochureItemId(), -1);
        service.moveItem(brochure.getBrochureId(), b.getBrochureItemId(), 1);

        assertThat(brochure.getItems()).containsExactly(a, b);
    }

    @Test
    void removingAnItemRenumbersThePositions() {
        BrochureItem a = itemFor(product("SPK-001", 1), 0);
        BrochureItem b = itemFor(product("SPK-002", 1), 1);
        BrochureItem c = itemFor(product("SPK-003", 1), 2);

        service.removeItem(brochure.getBrochureId(), b.getBrochureItemId());

        assertThat(brochure.getItems()).containsExactly(a, c);
        assertThat(brochure.getItems()).extracting(BrochureItem::getPosition).containsExactly(0, 1);
    }

    @Test
    void duplicatingCopiesTheItemsWithTheirTextsAndPhotos() {
        brochure.setIntroText("Calidad y estilo");
        Product p = product("SPK-001", 3);
        BrochureItem item = itemFor(p, 0);
        item.setHighlightText("Mi frase");
        item.setExtraImage1(p.getProductImages().get(2));

        UUID copyId = service.duplicate(brochure.getBrochureId());

        assertThat(copyId).isNotEqualTo(brochure.getBrochureId());
        Brochure copy = savedCopy();
        assertThat(copy.getName()).isEqualTo("Verano (copia)");
        assertThat(copy.getIntroText()).isEqualTo("Calidad y estilo");
        BrochureItem copied = copy.getItems().get(0);
        assertThat(copied.getBrochureItemId()).isNotEqualTo(item.getBrochureItemId());
        assertThat(copied.getHighlightText()).isEqualTo("Mi frase");
        assertThat(copied.getExtraImage1()).isSameAs(p.getProductImages().get(2));
    }

    // ---------------------------------------------------------------- datos para el PDF

    @Test
    void thePdfDataUsesTheLivePriceAndTheChosenPhotosAndTexts() {
        Product p = product("SPK-001", 4);
        p.setGeneralPrice(new java.math.BigDecimal("50.00"));
        p.setDiscountPercent(new java.math.BigDecimal("20"));
        BrochureItem item = itemFor(p, 0);
        item.setExtraImage1(p.getProductImages().get(2));
        item.setHighlightText("Clásico y elegante.\n\nEl suéter ideal.");
        item.setDetailsText("Punto pata de gallo.\nTallas: S y M");

        var storage = org.mockito.Mockito.mock(StorageService.class);
        when(storage.getPublicUrl(any())).thenAnswer(i -> "http://img/" + i.getArgument(0));
        var service2 = new BrochureBuilderService(brochures, products, mock(ProductService.class), storage,
                Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC));
        brochure.setIntroText("Calidad y estilo");

        var catalog = service2.pdfData(brochure.getBrochureId());

        assertThat(catalog.name()).isEqualTo("Verano");
        assertThat(catalog.hasIntro()).isTrue();
        assertThat(catalog.showPrices()).isTrue();
        var first = catalog.items().get(0);
        assertThat(first.code()).isEqualTo("SPK-001");
        assertThat(first.price()).isEqualByComparingTo("50.00");
        assertThat(first.finalPrice()).isEqualByComparingTo("40.00");
        assertThat(first.discountPercent()).isEqualTo(20);
        assertThat(first.hasDiscount()).isTrue();
        assertThat(first.mainUrl()).isEqualTo("http://img/products/SPK-001/0.webp");
        assertThat(first.extraUrls()).containsExactly("http://img/products/SPK-001/2.webp");
        assertThat(first.highlight()).containsExactly("Clásico y elegante.", "El suéter ideal."); // sin líneas vacías
        assertThat(first.details()).containsExactly("Punto pata de gallo.", "Tallas: S y M");
    }

    @Test
    void textsAreSplitIntoLinesWithoutBlankOnes() {
        assertThat(BrochureBuilderService.lines(null)).isEmpty();
        assertThat(BrochureBuilderService.lines("  \n ")).isEmpty();
        assertThat(BrochureBuilderService.lines("a\r\nb\n\n c ")).containsExactly("a", "b", "c");
    }

    private Brochure savedCopy() {
        var captor = org.mockito.ArgumentCaptor.forClass(Brochure.class);
        org.mockito.Mockito.verify(brochures).save(captor.capture());
        return captor.getValue();
    }
}
