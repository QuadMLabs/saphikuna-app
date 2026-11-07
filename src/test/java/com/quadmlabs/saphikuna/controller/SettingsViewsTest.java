package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.service.SettingsService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
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

/** Pantalla de Configuración. */
@WebMvcTest(SettingsController.class)
class SettingsViewsTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SettingsService settings;

    @Test
    void showsTheCurrentDays() throws Exception {
        when(settings.newProductDays()).thenReturn(30);

        mvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Configuración")))
                .andExpect(content().string(containsString("Días que un producto se muestra como nuevo")))
                .andExpect(content().string(containsString("value=\"30\"")));
    }

    @Test
    void savesValidDaysAndRedirectsWithAMessage() throws Exception {
        mvc.perform(post("/admin/settings").param("newProductDays", "45"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings"))
                .andExpect(flash().attribute("flashText", "La configuración se guardó correctamente."));
        verify(settings).saveNewProductDays(45);
    }

    @Test
    void rejectsDaysOutOfRangeOrEmptyAndKeepsWhatWasTyped() throws Exception {
        mvc.perform(post("/admin/settings").param("newProductDays", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Los días deben estar entre 1 y 365.")))
                .andExpect(content().string(containsString("value=\"0\"")));

        mvc.perform(post("/admin/settings").param("newProductDays", "400"))
                .andExpect(content().string(containsString("Los días deben estar entre 1 y 365.")));

        mvc.perform(post("/admin/settings").param("newProductDays", ""))
                .andExpect(content().string(containsString("Escribe cuántos días.")));

        verify(settings, never()).saveNewProductDays(anyInt());
    }

    @Test
    void theMenuHighlightsConfiguration() throws Exception {
        when(settings.newProductDays()).thenReturn(15);

        String html = mvc.perform(get("/admin/settings")).andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("href=\"/admin/settings\""));
        // exactamente un ítem del menú queda resaltado: el de Configuración
        org.junit.jupiter.api.Assertions.assertEquals(1, html.split("sidebar-item\s+is-active", -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.matches("(?s).*<a href=\"/admin/settings\" class=\"sidebar-item\s+is-active\".*"));
    }
}

