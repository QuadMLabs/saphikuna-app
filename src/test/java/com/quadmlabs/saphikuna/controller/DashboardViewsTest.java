package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.entity.Commission;
import com.quadmlabs.saphikuna.entity.Product;
import com.quadmlabs.saphikuna.service.CommissionRow;
import com.quadmlabs.saphikuna.service.CommissionService;
import com.quadmlabs.saphikuna.service.ProductService;
import com.quadmlabs.saphikuna.service.StorageService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inicio del panel. */
@WebMvcTest(DashboardController.class)
class DashboardViewsTest {

    static final LocalDate TODAY = LocalDate.now();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService productService;
    @MockitoBean
    CommissionService commissionService;
    @MockitoBean
    StorageService storageService;

    private CommissionRow row(String client, String title, long inDays, String status) {
        Commission c = new Commission();
        c.setCommissionId(UUID.randomUUID());
        c.setClientName(client);
        c.setTitle(title);
        c.setQuantity(10);
        c.setDueDate(TODAY.plusDays(inDays));
        c.setStatus(status);
        return CommissionRow.of(c, TODAY);
    }

    private void baseStats() {
        when(productService.stats()).thenReturn(new ProductService.ProductStats(20, 18, 2, 15, 3, 2, 4, 5));
        when(productService.countMissing(anyString())).thenReturn(0L);
        when(productService.recent(5)).thenReturn(List.of());
    }

    @Test
    void homeShowsQuickActionsAndProductSummary() throws Exception {
        baseStats();
        when(commissionService.deliveries()).thenReturn(
                new CommissionService.DeliverySummary(List.of(), List.of(), List.of(), 0));

        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nuevo producto")))
                .andExpect(content().string(containsString("Nuevo encargo")))
                .andExpect(content().string(containsString("href=\"/admin/products?availability=Agotado\"")))
                .andExpect(content().string(containsString("href=\"/admin/products?status=Inactivo\"")))
                .andExpect(content().string(containsString("<b>20</b>")))
                .andExpect(content().string(containsString("No tienes entregas pendientes")));
    }

    @Test
    void deliveriesAreShownInGroupsWithTheirClient() throws Exception {
        baseStats();
        when(commissionService.deliveries()).thenReturn(new CommissionService.DeliverySummary(
                List.of(row("Marca Vencida", "Bufandas", -3, Commission.IN_PROGRESS)),
                List.of(row("Marca Semana", "Chalecos", 2, Commission.TO_START)),
                List.of(row("Marca Lejana", "Gorros", 20, Commission.TO_START)),
                3));

        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vencidas")))
                .andExpect(content().string(containsString("Esta semana")))
                .andExpect(content().string(containsString("Próximos 30 días")))
                .andExpect(content().string(containsString("Marca Vencida")))
                .andExpect(content().string(containsString("Vencido hace 3 días")))
                .andExpect(content().string(containsString("Entrega en 2 días")))
                .andExpect(content().string(containsString("Marca Lejana")));
    }

    @Test
    void thingsToCompleteLinkToTheFilteredProductList() throws Exception {
        baseStats();
        when(productService.countMissing("photo")).thenReturn(3L);
        when(productService.countMissing("sizes")).thenReturn(1L);
        when(commissionService.deliveries()).thenReturn(
                new CommissionService.DeliverySummary(List.of(), List.of(), List.of(), 0));

        mvc.perform(get("/admin"))
                .andExpect(content().string(containsString("href=\"/admin/products?missing=photo\"")))
                .andExpect(content().string(containsString("href=\"/admin/products?missing=sizes\"")))
                .andExpect(content().string(containsString("Sin foto")))
                .andExpect(content().string(containsString("Sin tallas")))
                // lo que nadie tiene pendiente no aparece
                .andExpect(content().string(not(containsString("missing=category"))))
                .andExpect(content().string(not(containsString("Todos tus productos están completos"))));
    }

    @Test
    void whenNothingIsMissingItSaysSo() throws Exception {
        baseStats();
        when(commissionService.deliveries()).thenReturn(
                new CommissionService.DeliverySummary(List.of(), List.of(), List.of(), 0));

        mvc.perform(get("/admin"))
                .andExpect(content().string(containsString("Todos tus productos están completos.")));
    }

    @Test
    void recentProductsShowNameCodeAndThumbnail() throws Exception {
        baseStats();
        Product p = new Product();
        p.setProductId(UUID.randomUUID());
        p.setName("Suéter Coral");
        p.setCode("SPK-001");
        p.setAvailability(Product.AVAILABLE);
        p.setPrimaryImagePath("products/x/a.webp");
        p.setUpdatedAt(OffsetDateTime.now());
        when(productService.recent(5)).thenReturn(List.of(p));
        when(storageService.getThumbPublicUrl("products/x/a.webp")).thenReturn("https://cdn/a-thumb.webp");
        when(commissionService.deliveries()).thenReturn(
                new CommissionService.DeliverySummary(List.of(), List.of(), List.of(), 0));

        mvc.perform(get("/admin"))
                .andExpect(content().string(containsString("Suéter Coral")))
                .andExpect(content().string(containsString("SPK-001")))
                .andExpect(content().string(containsString("https://cdn/a-thumb.webp")))
                .andExpect(content().string(containsString("href=\"/admin/products/" + p.getProductId() + "/edit\"")));
    }

    @Test
    void homeStillWorksIfTheDeliveriesCannotBeRead() throws Exception {
        baseStats();
        when(commissionService.deliveries()).thenThrow(new IllegalStateException("la tabla no existe"));

        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No tienes entregas pendientes")))
                .andExpect(content().string(containsString("<b>20</b>")));
    }

    @Test
    void newProductsCountLinksToTheNewFilter() throws Exception {
        baseStats();
        when(commissionService.deliveries()).thenReturn(
                new CommissionService.DeliverySummary(List.of(), List.of(), List.of(), 0));

        mvc.perform(get("/admin"))
                .andExpect(content().string(containsString("href=\"/admin/products?news=true\"")))
                .andExpect(content().string(containsString("<b>5</b>")))
                .andExpect(content().string(containsString("Nuevos")))
                .andExpect(content().string(containsString("Modificados recientemente")));
    }
}
