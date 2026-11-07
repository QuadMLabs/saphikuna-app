package com.quadmlabs.saphikuna.controller;

import com.quadmlabs.saphikuna.catalog.CatalogAdapter;
import com.quadmlabs.saphikuna.catalog.CatalogField;
import com.quadmlabs.saphikuna.catalog.CatalogItem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantallas genéricas de catálogos, probadas con un catálogo de colores en memoria. */
@WebMvcTest(CatalogController.class)
@Import(PermissiveSecurity.class)
class CatalogViewsTest {

    static final UUID ROJO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID VINO = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Catálogo de colores falso: Rojo (en uso por 3 productos) y Vino (sin uso). */
    static class FakeColors implements CatalogAdapter {

        final List<CatalogItem> items = new ArrayList<>(List.of(
                new CatalogItem(ROJO, "Rojo", Map.of("name", "Rojo", "hex", "#D32F2F"), 3),
                new CatalogItem(VINO, "Vino", Map.of("name", "Vino", "hex", "#722F37"), 0)));

        public String key() {
            return "colors";
        }

        public List<CatalogField> fields() {
            return List.of(
                    new CatalogField("name", "catalog.field.name", CatalogField.Type.TEXT, true, 60),
                    new CatalogField("hex", "catalog.field.hex", CatalogField.Type.COLOR, true, 7));
        }

        public List<CatalogItem> list(String q) {
            return q == null || q.isBlank() ? items
                    : items.stream().filter(i -> i.name().toLowerCase().contains(q.toLowerCase())).toList();
        }

        public Optional<CatalogItem> find(UUID id) {
            return items.stream().filter(i -> i.id().equals(id)).findFirst();
        }

        public Map<String, String> defaults() {
            return new LinkedHashMap<>(Map.of("hex", "#808080"));
        }

        public Map<String, String> save(UUID id, Map<String, String> values) {
            Map<String, String> errors = new LinkedHashMap<>();
            if (values.getOrDefault("name", "").isBlank()) {
                errors.put("name", "catalog.validation.name.required");
            }
            return errors;
        }

        public DeleteResult delete(UUID id) {
            CatalogItem item = find(id).orElseThrow(() -> new IllegalArgumentException("x"));
            return item.usage() > 0 ? new DeleteResult(false, item.name(), item.usage())
                    : new DeleteResult(true, item.name(), 0);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        CatalogAdapter colors() {
            return new FakeColors();
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void listShowsRowsSwatchUsageAndTheNewButton() throws Exception {
        mvc.perform(get("/admin/colors"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Colores")))
                .andExpect(content().string(containsString("Nuevo color")))
                .andExpect(content().string(containsString("Rojo")))
                .andExpect(content().string(containsString("#D32F2F")))
                .andExpect(content().string(containsString("background:#D32F2F")))
                .andExpect(content().string(containsString("3 productos")))
                .andExpect(content().string(containsString("Sin uso")));
    }

    @Test
    void usedItemHasADisabledDeleteButtonAndUnusedOneHasTheConfirmation() throws Exception {
        String html = mvc.perform(get("/admin/colors")).andReturn().getResponse().getContentAsString();

        // Rojo (en uso): botón deshabilitado. Vino (sin uso): formulario de borrado.
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("disabled"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("/admin/colors/" + VINO + "/delete"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("/admin/colors/" + ROJO + "/delete"));
    }

    @Test
    void searchFiltersTheList() throws Exception {
        mvc.perform(get("/admin/colors").param("q", "vin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vino")))
                .andExpect(content().string(not(containsString("Rojo"))));
    }

    @Test
    void newFormUsesTheColorPickerWithADefault() throws Exception {
        mvc.perform(get("/admin/colors/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("type=\"color\"")))
                .andExpect(content().string(containsString("value=\"#808080\"")))
                .andExpect(content().string(containsString("Nuevo color")));
    }

    @Test
    void editFormIsPrefilled() throws Exception {
        mvc.perform(get("/admin/colors/" + ROJO + "/edit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Editar color")))
                .andExpect(content().string(containsString("value=\"Rojo\"")))
                .andExpect(content().string(containsString("value=\"#D32F2F\"")));
    }

    @Test
    void invalidSubmitShowsTheErrorAndKeepsTheForm() throws Exception {
        mvc.perform(post("/admin/colors").param("values[name]", " ").param("values[hex]", "#112233"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El nombre es obligatorio.")))
                .andExpect(content().string(containsString("value=\"#112233\"")));
    }

    @Test
    void validSubmitRedirectsToTheListWithASuccessMessage() throws Exception {
        mvc.perform(post("/admin/colors").param("values[name]", "Menta").param("values[hex]", "#98FF98"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/colors"))
                .andExpect(flash().attribute("flashText", "Se guardó correctamente."));
    }

    @Test
    void deletingAnItemInUseIsBlockedWithAClearMessage() throws Exception {
        mvc.perform(post("/admin/colors/" + ROJO + "/delete"))
                .andExpect(redirectedUrl("/admin/colors"))
                .andExpect(flash().attribute("flashError",
                        containsString("lo usan 3 producto(s)")));
    }

    @Test
    void deletingAnUnusedItemWorks() throws Exception {
        mvc.perform(post("/admin/colors/" + VINO + "/delete"))
                .andExpect(redirectedUrl("/admin/colors"))
                .andExpect(flash().attribute("flashText", "Se eliminó correctamente."));
    }

    @Test
    void unknownCatalogOrItemIsNotFound() throws Exception {
        mvc.perform(get("/admin/materiales")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/colors/" + UUID.randomUUID() + "/edit")).andExpect(status().isNotFound());
    }
}
