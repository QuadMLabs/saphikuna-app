package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.dto.ProductForm;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.repository.CategoryRepository;
import com.quadmlabs.saphikuna.repository.CollectionRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.repository.ProductRepository;
import com.quadmlabs.saphikuna.repository.SizeRepository;
import com.quadmlabs.saphikuna.service.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El código del producto lo asigna el servidor, es secuencial y no se puede editar. */
class ProductCodeTest {

    static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 11, 7);

    private ProductRepository products;
    private ProductServiceImpl service;
    private final com.quadmlabs.saphikuna.service.SettingsService settings = mock(com.quadmlabs.saphikuna.service.SettingsService.class);
    private final java.time.Clock clock = java.time.Clock.fixed(
            TODAY.atTime(10, 0).atZone(java.time.ZoneId.of("America/Guayaquil")).toInstant(),
            java.time.ZoneId.of("America/Guayaquil"));

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        service = new ProductServiceImpl(
                products,
                mock(CategoryRepository.class),
                mock(CollectionRepository.class),
                mock(ColorRepository.class),
                mock(SizeRepository.class),
                mock(StorageService.class),
                settings,
                clock,
                4);
    }

    private ProductForm form() {
        ProductForm f = new ProductForm();
        f.setName("Chompa nueva");
        f.setGeneralPrice(new BigDecimal("30"));
        return f;
    }

    private Product lastSaved() {
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(products, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    void newProductGetsNextNumberFromTheSequence() {
        when(products.nextCodeNumber()).thenReturn(21L);

        service.create(form());

        assertEquals("SPK-021", lastSaved().getCode());
    }

    @Test
    void codeSentInTheFormIsIgnored() {
        when(products.nextCodeNumber()).thenReturn(21L);
        ProductForm f = form();
        f.setCode("HACK-999");

        service.create(f);

        assertEquals("SPK-021", lastSaved().getCode());
    }

    @Test
    void skipsCodesThatAlreadyExist() {
        // La secuencia entrega 21, pero SPK-021 ya está tomado (ej. importado a mano)
        when(products.nextCodeNumber()).thenReturn(21L, 22L);
        when(products.existsByCodeIgnoreCase("SPK-021")).thenReturn(true);

        service.create(form());

        assertEquals("SPK-022", lastSaved().getCode());
    }

    @Test
    void numbersAboveNinetyNineKeepWorking() {
        when(products.nextCodeNumber()).thenReturn(1000L);

        service.create(form());

        assertEquals("SPK-1000", lastSaved().getCode());
    }

    @Test
    void editingKeepsTheExistingCodeAndDoesNotConsumeANumber() {
        Product existing = new Product();
        existing.setProductId(UUID.randomUUID());
        existing.setCode("SPK-007");
        when(products.findById(existing.getProductId())).thenReturn(Optional.of(existing));

        ProductForm f = form();
        f.setCode("OTRO-1"); // aunque lo manipulen
        service.update(existing.getProductId(), f);

        assertEquals("SPK-007", existing.getCode());
        verify(products, never()).nextCodeNumber();
    }

    @Test
    void legacyProductWithoutCodeGetsOneWhenEdited() {
        Product legacy = new Product();
        legacy.setProductId(UUID.randomUUID());
        when(products.findById(legacy.getProductId())).thenReturn(Optional.of(legacy));
        when(products.nextCodeNumber()).thenReturn(21L);

        service.update(legacy.getProductId(), form());

        assertEquals("SPK-021", legacy.getCode());
        verify(products, times(1)).nextCodeNumber();
    }

    // ---------------- "Nuevo hasta" ----------------

    @Test
    void aNewProductFormDefaultsToTodayPlusTheConfiguredDays() {
        when(settings.newProductDays()).thenReturn(15);

        ProductForm f = service.newForm();

        assertEquals(TODAY.plusDays(15), f.getNewUntil());
    }

    @Test
    void theDefaultFollowsWhateverDaysTheUserConfigured() {
        when(settings.newProductDays()).thenReturn(45);

        assertEquals(TODAY.plusDays(45), service.newForm().getNewUntil());
    }

    @Test
    void createStoresTheNewUntilDateFromTheForm() {
        when(products.nextCodeNumber()).thenReturn(21L);
        ProductForm f = form();
        f.setNewUntil(TODAY.plusDays(20));

        service.create(f);

        assertEquals(TODAY.plusDays(20), lastSaved().getNewUntil());
    }

    @Test
    void anEmptyNewUntilMeansTheProductIsNotNew() {
        when(products.nextCodeNumber()).thenReturn(21L);
        ProductForm f = form();
        f.setNewUntil(null);

        service.create(f);

        assertEquals(null, lastSaved().getNewUntil());
        assertEquals(false, lastSaved().isNewOn(TODAY));
    }

    @Test
    void isNewOnIncludesTheLastDay() {
        Product p = new Product();
        p.setNewUntil(TODAY);
        assertEquals(true, p.isNewOn(TODAY));
        assertEquals(false, p.isNewOn(TODAY.plusDays(1)));
        assertEquals(true, p.isNewOn(TODAY.minusDays(5)));
    }
}
