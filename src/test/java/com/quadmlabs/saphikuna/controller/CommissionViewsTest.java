package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.CommissionForm;
import com.quadmlabs.saphikuna.entity.Commission;
import com.quadmlabs.saphikuna.service.CommissionRow;
import com.quadmlabs.saphikuna.service.CommissionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantallas de Encargos y contador de entregas urgentes en el menú. */
@WebMvcTest(CommissionController.class)
@Import(PermissiveSecurity.class)
class CommissionViewsTest {

    static final LocalDate TODAY = LocalDate.now();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CommissionService service;

    private Commission commission(String client, String title, LocalDate due, String status, String price, String advance) {
        Commission c = new Commission();
        c.setCommissionId(UUID.randomUUID());
        c.setClientName(client);
        c.setTitle(title);
        c.setQuantity(25);
        c.setDueDate(due);
        c.setStatus(status);
        c.setAgreedPrice(price == null ? null : new BigDecimal(price));
        c.setAdvancePaid(new BigDecimal(advance));
        return c;
    }

    private CommissionRow row(Commission c) {
        return CommissionRow.of(c, TODAY);
    }

    @Test
    void listShowsDueChipsDateMoneyAndBalance() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of(
                row(commission("Marca Andina", "40 bufandas", TODAY.minusDays(2), Commission.IN_PROGRESS, "800.00", "300.00")),
                row(commission("Boutique Sol", "Chalecos", TODAY.plusDays(3), Commission.TO_START, null, "0")),
                row(commission("Taller Luna", "Gorros", TODAY, Commission.READY, "100.00", "100.00"))));

        mvc.perform(get("/admin/commissions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marca Andina")))
                .andExpect(content().string(containsString("Vencido hace 2 días")))
                .andExpect(content().string(containsString("Entrega en 3 días")))
                .andExpect(content().string(containsString("Entrega hoy")))
                .andExpect(content().string(containsString("due-overdue")))
                .andExpect(content().string(containsString("$800.00")))
                .andExpect(content().string(containsString("Falta $500.00")))
                .andExpect(content().string(containsString("Pagado")))
                .andExpect(content().string(containsString("Nuevo encargo")));
    }

    @Test
    void listDefaultsToPendingAndIgnoresUnknownStatusFilters() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of());

        mvc.perform(get("/admin/commissions")).andExpect(status().isOk());
        verify(service).search(any(), eq("active"));

        mvc.perform(get("/admin/commissions").param("status", "inventado")).andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2)).search(any(), eq("active"));

        mvc.perform(get("/admin/commissions").param("status", "Listo")).andExpect(status().isOk());
        verify(service).search(any(), eq("Listo"));
    }

    @Test
    void emptyStateChangesWithFilters() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of());

        mvc.perform(get("/admin/commissions"))
                .andExpect(content().string(containsString("No tienes encargos pendientes.")));
        mvc.perform(get("/admin/commissions").param("q", "xyz"))
                .andExpect(content().string(containsString("No se encontraron encargos.")));
    }

    @Test
    void newFormHasDatePickerStatusChipsAndClientSuggestions() throws Exception {
        when(service.clientNames()).thenReturn(List.of("Marca Andina", "Taller Luna"));

        mvc.perform(get("/admin/commissions/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("type=\"date\"")))
                .andExpect(content().string(containsString("value=\"Por empezar\"")))
                .andExpect(content().string(containsString("value=\"Entregado\"")))
                .andExpect(content().string(containsString("<option value=\"Marca Andina\">")));
    }

    @Test
    void editFormIsPrefilled() throws Exception {
        Commission c = commission("Marca Andina", "40 bufandas", TODAY.plusDays(10), Commission.IN_PROGRESS, "800.00", "300.00");
        CommissionForm form = new CommissionForm();
        form.setClientName("Marca Andina");
        form.setTitle("40 bufandas");
        form.setQuantity(25);
        form.setDueDate(TODAY.plusDays(10));
        form.setStatus(Commission.IN_PROGRESS);
        form.setAgreedPrice(new BigDecimal("800.00"));
        form.setAdvancePaid(new BigDecimal("300.00"));
        when(service.find(c.getCommissionId())).thenReturn(Optional.of(row(c)));
        when(service.toForm(c.getCommissionId())).thenReturn(form);

        mvc.perform(get("/admin/commissions/" + c.getCommissionId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Editar encargo")))
                .andExpect(content().string(containsString("value=\"Marca Andina\"")))
                .andExpect(content().string(containsString("value=\"" + TODAY.plusDays(10) + "\"")))
                .andExpect(content().string(containsString("value=\"800.00\"")));
    }

    @Test
    void submittingWithoutRequiredFieldsShowsErrorsAndSavesNothing() throws Exception {
        mvc.perform(post("/admin/commissions").param("clientName", " ").param("title", "").param("quantity", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El cliente es obligatorio.")))
                .andExpect(content().string(containsString("Describe el trabajo")))
                .andExpect(content().string(containsString("La fecha de entrega es obligatoria.")));
        verify(service, never()).create(any());
    }

    @Test
    void advanceCannotBeGreaterThanTheAgreedPrice() throws Exception {
        mvc.perform(post("/admin/commissions")
                .param("clientName", "Marca").param("title", "Trabajo").param("quantity", "5")
                .param("dueDate", TODAY.plusDays(5).toString()).param("status", "Por empezar")
                .param("agreedPrice", "100").param("advancePaid", "150"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El adelanto no puede ser mayor que el precio acordado.")));
        verify(service, never()).create(any());
    }

    @Test
    void anInvalidStatusIsRejected() throws Exception {
        mvc.perform(post("/admin/commissions")
                .param("clientName", "Marca").param("title", "Trabajo").param("quantity", "5")
                .param("dueDate", TODAY.plusDays(5).toString()).param("status", "inventado")
                .param("advancePaid", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elige un estado válido.")));
        verify(service, never()).create(any());
    }

    @Test
    void validSubmitSavesAndRedirectsWithAMessage() throws Exception {
        mvc.perform(post("/admin/commissions")
                .param("clientName", "Marca").param("title", "Trabajo").param("quantity", "5")
                .param("dueDate", TODAY.plusDays(5).toString()).param("status", "En proceso")
                .param("agreedPrice", "100").param("advancePaid", "40"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/commissions"))
                .andExpect(flash().attribute("flashText", "El encargo se guardó correctamente."));
        verify(service).create(any());
    }

    @Test
    void deleteRedirectsWithAMessage() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/admin/commissions/" + id + "/delete"))
                .andExpect(redirectedUrl("/admin/commissions"))
                .andExpect(flash().attribute("flashText", "El encargo se eliminó correctamente."));
        verify(service).delete(id);
    }

    @Test
    void editingAMissingCommissionIsNotFound() throws Exception {
        when(service.find(any())).thenReturn(Optional.empty());
        mvc.perform(get("/admin/commissions/" + UUID.randomUUID() + "/edit")).andExpect(status().isNotFound());
    }

    // ---------------- contador del menú ----------------

    @Test
    void menuShowsTheCountOfUrgentDeliveriesWhenThereAreSome() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of());
        when(service.urgentCount()).thenReturn(3L);

        mvc.perform(get("/admin/commissions"))
                .andExpect(content().string(containsString("menu-badge")))
                .andExpect(content().string(containsString(">3<")));
    }

    @Test
    void menuHasNoBadgeWithoutUrgentDeliveries() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of());
        when(service.urgentCount()).thenReturn(0L);

        mvc.perform(get("/admin/commissions"))
                .andExpect(content().string(not(containsString("class=\"menu-badge\""))));
    }

    @Test
    void thePanelKeepsWorkingIfTheCountFails() throws Exception {
        when(service.search(any(), any())).thenReturn(List.of());
        when(service.urgentCount()).thenThrow(new IllegalStateException("la tabla no existe"));

        mvc.perform(get("/admin/commissions"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"menu-badge\""))));
    }
}
