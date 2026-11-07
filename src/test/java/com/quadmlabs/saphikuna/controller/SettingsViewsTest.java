package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.service.SettingsService;
import com.quadmlabs.saphikuna.service.SystemStatusService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantalla de Configuración: tarjetas independientes. */
@WebMvcTest(SettingsController.class)
class SettingsViewsTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SettingsService settings;
    @MockitoBean
    SystemStatusService systemStatus;

    private StoreInfoForm store() {
        StoreInfoForm s = new StoreInfoForm();
        s.setStoreName("Saphikuna");
        s.setTagline("Tejiendo sueños, vistiendo raíces");
        s.setWhatsapp("+593 99 123 4567");
        s.setInstagram("@saphikuna");
        s.setEmail("hola@saphikuna.com");
        return s;
    }

    private SystemStatusService.SystemStatus systemStatusOf(boolean db, boolean storage) {
        return new SystemStatusService.SystemStatus(db, 12, storage, 230, 20, 75, "America/Guayaquil", LocalDate.of(2026, 11, 7));
    }

    @BeforeEach
    void setUp() {
        when(settings.newProductDays()).thenReturn(15);
        when(settings.storeInfo()).thenReturn(store());
        when(systemStatus.check()).thenReturn(systemStatusOf(true, true));
    }

    // ---------------- pantalla ----------------

    @Test
    void showsTheThreeCardsWithTheirCurrentValues() throws Exception {
        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"new-days\"")))
                .andExpect(content().string(containsString("id=\"store\"")))
                .andExpect(content().string(containsString("id=\"status\"")))
                .andExpect(content().string(containsString("value=\"15\"")))
                .andExpect(content().string(containsString("value=\"+593 99 123 4567\"")))
                .andExpect(content().string(containsString("value=\"@saphikuna\"")))
                .andExpect(content().string(containsString("href=\"#store\"")));
    }

    @Test
    void eachEditableCardHasItsOwnSaveFormAndTheStatusCardHasNone() throws Exception {
        String html = mvc.perform(get("/admin/settings")).andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/admin/settings/new-days\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("action=\"/admin/settings/store\""));
        // el estado es solo lectura: ninguna otra acción
        org.junit.jupiter.api.Assertions.assertEquals(2, html.split("method=\"post\"", -1).length - 1);
    }

    @Test
    void theMenuHighlightsConfiguration() throws Exception {
        String html = mvc.perform(get("/admin/settings")).andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(1, html.split("sidebar-item\\s+is-active", -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.matches("(?s).*<a href=\"/admin/settings\" class=\"sidebar-item\\s+is-active\".*"));
    }

    // ---------------- tarjeta: productos nuevos ----------------

    @Test
    void savesValidDaysAndReturnsToThatCard() throws Exception {
        mvc.perform(post("/admin/settings/new-days").param("newProductDays", "45"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings#new-days"))
                .andExpect(flash().attribute("flashText", "La configuración se guardó correctamente."));
        verify(settings).saveNewProductDays(45);
        verify(settings, never()).saveStoreInfo(any());
    }

    @Test
    void rejectsDaysOutOfRangeOrEmptyAndKeepsWhatWasTyped() throws Exception {
        mvc.perform(post("/admin/settings/new-days").param("newProductDays", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Los días deben estar entre 1 y 365.")))
                .andExpect(content().string(containsString("value=\"0\"")))
                // la otra tarjeta sigue mostrando lo guardado
                .andExpect(content().string(containsString("value=\"@saphikuna\"")));

        mvc.perform(post("/admin/settings/new-days").param("newProductDays", "400"))
                .andExpect(content().string(containsString("Los días deben estar entre 1 y 365.")));

        mvc.perform(post("/admin/settings/new-days").param("newProductDays", ""))
                .andExpect(content().string(containsString("Escribe cuántos días.")));

        verify(settings, never()).saveNewProductDays(anyInt());
    }

    // ---------------- tarjeta: datos de la tienda ----------------

    /** Envío válido del formulario de la tienda; {@code overrides} reemplaza campos (param() sumaría valores). */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder storePost(String... overrides) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("storeName", "Saphikuna Tejidos");
        values.put("tagline", "Frase");
        values.put("whatsapp", "+593 99 123 4567");
        values.put("phone", "");
        values.put("email", "hola@saphikuna.com");
        values.put("instagram", "@saphikuna");
        values.put("facebook", "https://facebook.com/saphikuna");
        values.put("address", "Quito");
        for (int i = 0; i < overrides.length; i += 2) {
            values.put(overrides[i], overrides[i + 1]);
        }
        var request = post("/admin/settings/store");
        values.forEach(request::param);
        return request;
    }

    @Test
    void savesTheStoreDataAndReturnsToThatCard() throws Exception {
        mvc.perform(storePost())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings#store"))
                .andExpect(flash().attribute("flashText", "La configuración se guardó correctamente."));

        ArgumentCaptor<StoreInfoForm> captor = ArgumentCaptor.forClass(StoreInfoForm.class);
        verify(settings).saveStoreInfo(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Saphikuna Tejidos", captor.getValue().getStoreName());
        org.junit.jupiter.api.Assertions.assertEquals("https://facebook.com/saphikuna", captor.getValue().getFacebook());
        verify(settings, never()).saveNewProductDays(anyInt());
    }

    @Test
    void theStoreNameIsRequired() throws Exception {
        mvc.perform(storePost("storeName", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El nombre de la tienda es obligatorio.")));
        verify(settings, never()).saveStoreInfo(any());
    }

    @Test
    void invalidWhatsappEmailAndSocialLinksAreRejectedWithClearMessages() throws Exception {
        mvc.perform(storePost("whatsapp", "llamame", "email", "no-es-correo",
                "instagram", "https://", "facebook", "con espacios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escribe solo números, con código de país.")))
                .andExpect(content().string(containsString("Escribe un correo válido.")))
                .andExpect(content().string(containsString("Escribe el usuario (@saphikuna) o el enlace completo")))
                // y conserva lo que se escribió para corregirlo
                .andExpect(content().string(containsString("value=\"llamame\"")));
        verify(settings, never()).saveStoreInfo(any());
    }

    @Test
    void contactFieldsAreOptionalAndAcceptUsernamesOrFullLinks() throws Exception {
        mvc.perform(storePost("whatsapp", "", "email", "", "instagram", "saphikuna",
                "facebook", "saphikuna.tejidos"))
                .andExpect(status().is3xxRedirection());
        verify(settings).saveStoreInfo(any());
    }

    // ---------------- tarjeta: estado del sistema ----------------

    @Test
    void statusShowsTheChecksTotalsAndEstimatedSpace() throws Exception {
        mvc.perform(get("/admin/settings"))
                .andExpect(content().string(containsString("Todo funciona")))
                .andExpect(content().string(containsString("Funciona (12 ms)")))
                .andExpect(content().string(containsString("Funciona (230 ms)")))
                .andExpect(content().string(containsString("America/Guayaquil · 07/11/2026")))
                .andExpect(content().string(containsString("6.8 MB de 1024 MB")))   // 75 fotos x 0,09
                .andExpect(content().string(not(containsString("Hay un problema"))));
    }

    @Test
    void statusExplainsWhenSomethingDoesNotRespond() throws Exception {
        when(systemStatus.check()).thenReturn(
                new SystemStatusService.SystemStatus(true, 10, false, 3000, 20, 75, "America/Guayaquil", LocalDate.of(2026, 11, 7)));

        mvc.perform(get("/admin/settings"))
                .andExpect(content().string(containsString("Hay un problema")))
                .andExpect(content().string(containsString("No responde")))
                .andExpect(content().string(containsString("Revisa la conexión a internet")));
    }

    @Test
    void theWholePageStillRendersIfTotalsCouldNotBeRead() throws Exception {
        when(systemStatus.check()).thenReturn(
                new SystemStatusService.SystemStatus(false, 3000, false, 3000, -1, -1, "America/Guayaquil", LocalDate.of(2026, 11, 7)));

        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hay un problema")))
                .andExpect(content().string(containsString("—")))
                .andExpect(content().string(not(containsString("MB de 1024 MB"))));
    }
}
