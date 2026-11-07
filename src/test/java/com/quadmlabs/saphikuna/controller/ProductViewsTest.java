package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.ProductForm;
import com.quadmlabs.saphikuna.entity.Category;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.entity.ProductColor;
import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.CollectionService;
import com.quadmlabs.saphikuna.service.ColorService;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.SizeService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica que las vistas de productos se renderizan (sin base de datos). */
@WebMvcTest(ProductController.class)
class ProductViewsTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService productService;
    @MockitoBean
    CategoryService categoryService;
    @MockitoBean
    CollectionService collectionService;
    @MockitoBean
    ColorService colorService;
    @MockitoBean
    SizeService sizeService;
    @MockitoBean
    StorageService storageService;

    private Color color() {
        Color c = new Color();
        c.setColorId(UUID.randomUUID());
        c.setName("Coral");
        c.setHexCode("#ff7f50");
        return c;
    }

    @Test
    void listRenders() throws Exception {
        Category cat = new Category();
        cat.setCategoryId(UUID.randomUUID());
        cat.setName("Suéter");

        Color color = color();

        Product p = new Product();
        p.setProductId(UUID.randomUUID());
        p.setName("Suéter Coral");
        p.setCode("SPK-001");
        p.setStatus(Product.ACTIVE);
        p.setAvailability(Product.ON_REQUEST);
        p.setGeneralPrice(new BigDecimal("49.00"));
        p.setDiscountPercent(new BigDecimal("20"));
        p.setCategory(cat);
        ProductColor pc = new ProductColor();
        pc.setColor(color);
        p.getProductColors().add(pc);

        when(productService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 12), 30));
        when(categoryService.findAll()).thenReturn(List.of(cat));
        when(collectionService.findAll()).thenReturn(List.of());

        mvc.perform(get("/admin/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Suéter Coral")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("$39.20")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bajo pedido")));
    }

    @Test
    void newFormRenders() throws Exception {
        ProductForm form = new ProductForm();
        form.setCode("SPK-002");
        form.getSpecs().add(new ProductForm.SpecRow());

        when(productService.newForm()).thenReturn(form);
        when(categoryService.findAll()).thenReturn(List.of());
        when(collectionService.findAll()).thenReturn(List.of());
        when(colorService.findAll()).thenReturn(List.of(color()));
        com.quadmlabs.saphikuna.entity.Size xxl = new com.quadmlabs.saphikuna.entity.Size();
        xxl.setSizeId(UUID.randomUUID());
        xxl.setName("XXL");
        when(sizeService.findAll()).thenReturn(List.of(xxl));

        mvc.perform(get("/admin/products/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SPK-002")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("XXL")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Coral")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"newImages\"")));
    }

    @Test
    void invalidSubmitShowsErrorsAndKeepsForm() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of());
        when(collectionService.findAll()).thenReturn(List.of());
        when(colorService.findAll()).thenReturn(List.of());
        when(sizeService.findAll()).thenReturn(List.of());

        // Sin nombre ni precio: vuelve al formulario con errores
        mvc.perform(multipart("/admin/products")
                .param("code", "SPK-009"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("obligatorio")));
    }

    private Product product(String name) {
        Product p = new Product();
        p.setProductId(UUID.randomUUID());
        p.setName(name);
        p.setStatus(Product.ACTIVE);
        p.setAvailability(Product.AVAILABLE);
        p.setGeneralPrice(new BigDecimal("10.00"));
        return p;
    }

    private void emptyCatalogs() {
        when(categoryService.findAll()).thenReturn(List.of());
        when(collectionService.findAll()).thenReturn(List.of());
    }

    @Test
    void pagerShowsPageXofYAndKeepsFilters() throws Exception {
        emptyCatalogs();
        when(productService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product("Chompa")), PageRequest.of(0, 12), 30));

        mvc.perform(get("/admin/products").param("q", "chompa").param("availability", "Agotado"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Página 1 de 3")))
                // el enlace a la página siguiente conserva los filtros
                .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("q=chompa")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("availability=Agotado")));
    }

    @Test
    void pageBeyondTheEndFallsBackToLastPage() throws Exception {
        emptyCatalogs();
        // 25 productos = 3 páginas (0..2). Pedir la 9 devuelve vacío y debe caer en la 2.
        when(productService.search(any(), any())).thenAnswer(inv -> {
            org.springframework.data.domain.Pageable pageable = inv.getArgument(1);
            return pageable.getPageNumber() == 2
                    ? new PageImpl<>(List.of(product("Ultimo producto")), pageable, 25)
                    : new PageImpl<Product>(List.of(), pageable, 25);
        });

        mvc.perform(get("/admin/products").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ultimo producto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Página 3 de 3")));
    }

    @Test
    void invalidQueryParamsAreIgnoredInsteadOfFailing() throws Exception {
        emptyCatalogs();
        when(productService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product("Chompa")), PageRequest.of(0, 12), 1));

        mvc.perform(get("/admin/products")
                .param("page", "abc").param("categoryId", "no-es-uuid").param("collectionId", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chompa")));
    }

    @Test
    void emptyListShowsDifferentMessageWithAndWithoutFilters() throws Exception {
        emptyCatalogs();
        when(productService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        mvc.perform(get("/admin/products"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No hay productos registrados.")));
        mvc.perform(get("/admin/products").param("q", "x"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No se encontraron productos.")));
    }

    @Test
    void listIsSortedByCodeAscending() throws Exception {
        emptyCatalogs();
        when(productService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(product("Chompa")), PageRequest.of(0, 12), 1));

        mvc.perform(get("/admin/products")).andExpect(status().isOk());

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.verify(productService).search(any(), captor.capture());

        org.springframework.data.domain.Sort sort = captor.getValue().getSort();
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.data.domain.Sort.Direction.ASC, sort.getOrderFor("code").getDirection());
        org.junit.jupiter.api.Assertions.assertNotNull(sort.getOrderFor("createdAt"));
    }

    @Test
    void thumbnailOpensTheFullPhotoInAPopoverWithoutJs() throws Exception {
        emptyCatalogs();
        Product p = product("Suéter Coral");
        p.setPrimaryImagePath("products/x/foto.webp");
        when(productService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 12), 1));
        when(storageService.getThumbPublicUrl("products/x/foto.webp")).thenReturn("https://cdn/foto-thumb.webp");
        when(storageService.getPublicUrl("products/x/foto.webp")).thenReturn("https://cdn/foto.webp");

        mvc.perform(get("/admin/products"))
                .andExpect(status().isOk())
                // la miniatura es el botón que abre el popover
                .andExpect(content().string(org.hamcrest.Matchers.containsString("popovertarget=\"photo-" + p.getProductId() + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("src=\"https://cdn/foto-thumb.webp\"")))
                // la foto completa solo vive dentro del popover
                .andExpect(content().string(org.hamcrest.Matchers.containsString("src=\"https://cdn/foto.webp\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("popover")));
    }
}
