package com.quadmlabs.saphikuna.brochure.builder;

import com.quadmlabs.saphikuna.brochure.BrochurePdfService;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.BuilderException;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.Candidate;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.ItemEdit;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.ItemRow;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.Overview;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.PhotoOption;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.Summary;
import com.quadmlabs.saphikuna.controller.PermissiveSecurity;
import com.quadmlabs.saphikuna.dto.StoreInfoForm;
import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.SettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantallas del armado de catálogos: lista, datos, elegir prendas y ficha de cada prenda. */
@WebMvcTest(BrochureBuilderController.class)
@Import({ PermissiveSecurity.class, BrochureBuilderViewsTest.FixedClock.class })
class BrochureBuilderViewsTest {

    /** El controlador usa el reloj para la fecha de la portada del PDF. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC);
        }
    }


    @Autowired
    MockMvc mvc;

    @MockitoBean
    BrochureBuilderService builder;
    @MockitoBean
    SettingsService settings;
    @MockitoBean
    CategoryService categories;
    @MockitoBean
    BrochurePdfService pdf;

    private final UUID id = UUID.randomUUID();
    private final UUID itemA = UUID.randomUUID();
    private final UUID itemB = UUID.randomUUID();
    private final UUID photo1 = UUID.randomUUID();
    private final UUID photo2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        StoreInfoForm store = new StoreInfoForm();
        store.setStoreName("Saphikuna");
        store.setTagline("Tejiendo sueños, vistiendo raíces");
        when(settings.storeInfo()).thenReturn(store);

        BrochureForm form = new BrochureForm();
        form.setName("Verano 2026");
        form.setIntroText("Calidad y estilo");
        when(builder.toForm(id)).thenReturn(form);
        when(builder.overview(id)).thenReturn(new Overview(id, "Verano 2026", "Calidad y estilo", true, List.of(
                new ItemRow(itemA, UUID.randomUUID(), "SPK-001", "Suéter Coral", "http://t/1", 0, 3, true, "Disponible"),
                new ItemRow(itemB, UUID.randomUUID(), "SPK-002", "Blusa Gaia", null, 1, 1, false, "Agotado"))));
    }

    private ItemEdit edit(UUID next, ItemForm form) {
        return new ItemEdit(id, itemA, UUID.randomUUID(), "SPK-001", "Suéter Coral", "Chompas", "http://t/1",
                List.of(new PhotoOption(photo1, "http://t/p1", "main"), new PhotoOption(photo2, "http://t/p2", "none")),
                form, null, next, 1, 2);
    }

    private ItemForm itemForm() {
        ItemForm form = new ItemForm();
        form.setHighlightText("Clásico y elegante.");
        form.setDetailsText("Hilo angora\nTallas: S y M");
        return form;
    }

    // ---------------- lista y nuevo ----------------

    @Test
    void theListShowsEachCatalogWithItsPieceCount() throws Exception {
        when(builder.list()).thenReturn(List.of(new Summary(id, "Verano 2026", 12, OffsetDateTime.parse("2026-11-08T10:00:00Z"), null)));

        mvc.perform(get("/admin/catalogos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verano 2026")))
                .andExpect(content().string(containsString("12 prendas")))
                .andExpect(content().string(containsString("/admin/catalogos/" + id + "/duplicar")))
                .andExpect(content().string(containsString("/admin/catalogos/" + id + "/eliminar")));
    }

    @Test
    void anEmptyListInvitesToCreateTheFirstCatalog() throws Exception {
        when(builder.list()).thenReturn(List.of());

        mvc.perform(get("/admin/catalogos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crear mi primer catálogo")));
    }

    @Test
    void theNewFormAsksOnlyForNameIntroAndPrices() throws Exception {
        mvc.perform(get("/admin/catalogos/nuevo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"name\"")))
                .andExpect(content().string(containsString("name=\"introText\"")))
                .andExpect(content().string(containsString("name=\"showPrices\"")));
    }

    @Test
    void creatingGoesStraightToChoosingPieces() throws Exception {
        when(builder.create(any())).thenReturn(id);

        mvc.perform(post("/admin/catalogos").param("name", "Verano 2026").param("introText", "Hola"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id + "/agregar"));
    }

    @Test
    void aCatalogNeedsAName() throws Exception {
        mvc.perform(post("/admin/catalogos").param("name", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escribe el nombre del catálogo")));

        verify(builder, never()).create(any());
    }

    // ---------------- datos y prendas ----------------

    @Test
    void theOverviewShowsTheBrandTheDataFormAndTheItems() throws Exception {
        mvc.perform(get("/admin/catalogos/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tejiendo sueños, vistiendo raíces")))   // eslogan de Configuración
                .andExpect(content().string(containsString("value=\"Verano 2026\"")))
                .andExpect(content().string(containsString("Prendas (2)")))
                .andExpect(content().string(containsString("Suéter Coral")))
                .andExpect(content().string(containsString("Textos listos")))
                .andExpect(content().string(containsString("Falta completar los textos")))
                .andExpect(content().string(containsString("/admin/catalogos/" + id + "/prendas/" + itemA)))
                .andExpect(content().string(containsString("/admin/catalogos/" + id + "/agregar")));
    }

    @Test
    void anUnknownCatalogIsNotFound() throws Exception {
        UUID other = UUID.randomUUID();
        when(builder.overview(other)).thenThrow(new BuilderException(null, "brochures.notfound"));

        mvc.perform(get("/admin/catalogos/" + other)).andExpect(status().isNotFound());
    }

    @Test
    void savingTheDataComesBackToTheOverview() throws Exception {
        mvc.perform(post("/admin/catalogos/" + id).param("name", "Verano 2027").param("showPrices", "true"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id))
                .andExpect(flash().attributeExists("flashText"));

        verify(builder).update(eq(id), any());
    }

    @Test
    void duplicatingOpensTheCopy() throws Exception {
        UUID copy = UUID.randomUUID();
        when(builder.duplicate(id)).thenReturn(copy);

        mvc.perform(post("/admin/catalogos/" + id + "/duplicar")).andExpect(redirectedUrl("/admin/catalogos/" + copy));
    }

    @Test
    void movingAndRemovingComeBackToTheOverview() throws Exception {
        mvc.perform(post("/admin/catalogos/" + id + "/prendas/" + itemA + "/mover").param("dir", "down"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id + "#prenda-" + itemA));
        verify(builder).moveItem(id, itemA, 1);

        mvc.perform(post("/admin/catalogos/" + id + "/prendas/" + itemB + "/quitar"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id));
        verify(builder).removeItem(id, itemB);
    }

    // ---------------- elegir prendas ----------------

    @Test
    void thePickerListsThePiecesAndMarksTheOnesAlreadyInside() throws Exception {
        when(builder.candidates(eq(id), any(), any())).thenReturn(List.of(
                new Candidate(UUID.randomUUID(), "SPK-001", "Suéter Coral", "Chompas", "http://t/1", "Disponible", 4, true),
                new Candidate(UUID.randomUUID(), "SPK-002", "Blusa Gaia", "Blusas", null, "Agotado", 2, false)));

        String html = mvc.perform(get("/admin/catalogos/" + id + "/agregar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"ids\"")))
                .andExpect(content().string(containsString("Ya está")))
                .andExpect(content().string(containsString("Agotado")))
                .andExpect(content().string(containsString("Agregar las marcadas")))
                .andExpect(content().string(containsString("Agregar todas")))
                .andReturn().getResponse().getContentAsString();

        // la que ya está va marcada y bloqueada: no se puede agregar dos veces
        assertThat(html).containsPattern("(?s)checked=\"checked\".*?disabled=\"disabled\"|disabled=\"disabled\".*?checked=\"checked\"");
    }

    @Test
    void addingWithoutPickingAnythingExplainsWhatToDo() throws Exception {
        mvc.perform(post("/admin/catalogos/" + id + "/agregar"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id + "/agregar"))
                .andExpect(flash().attribute("flashError", "Marca al menos una prenda."));

        verify(builder, never()).addProducts(any(), any());
    }

    @Test
    void addingTheMarkedPiecesGoesToTheOverview() throws Exception {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(builder.addProducts(eq(id), any())).thenReturn(2);

        mvc.perform(post("/admin/catalogos/" + id + "/agregar").param("ids", p1.toString(), p2.toString()))
                .andExpect(redirectedUrl("/admin/catalogos/" + id))
                .andExpect(flash().attribute("flashText", "Se agregaron 2 prendas. Ahora revisa la ficha de cada una."));
    }

    @Test
    void addAllUsesTheCurrentSearchAndCategory() throws Exception {
        UUID category = UUID.randomUUID();
        when(builder.addAll(eq(id), anyString(), eq(category))).thenReturn(5);

        mvc.perform(post("/admin/catalogos/" + id + "/agregar").param("all", "true").param("q", "azul")
                .param("categoria", category.toString()))
                .andExpect(redirectedUrl("/admin/catalogos/" + id));

        verify(builder).addAll(id, "azul", category);
    }

    // ---------------- ficha de la prenda ----------------

    @Test
    void theItemFormShowsOneRoleControlPerPhotoAndBothTexts() throws Exception {
        when(builder.itemEdit(id, itemA)).thenReturn(edit(itemB, itemForm()));

        mvc.perform(get("/admin/catalogos/" + id + "/prendas/" + itemA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prenda 1 de 2")))
                .andExpect(content().string(containsString("name=\"role_" + photo1 + "\"")))
                .andExpect(content().string(containsString("name=\"role_" + photo2 + "\"")))
                .andExpect(content().string(containsString("name=\"highlightText\"")))
                .andExpect(content().string(containsString("Clásico y elegante.")))
                .andExpect(content().string(containsString("Hilo angora")))
                .andExpect(content().string(containsString("Guardar y siguiente")))
                .andExpect(content().string(containsString("Usar la descripción del producto")));
    }

    @Test
    void theLastItemHasNoSaveAndNextButton() throws Exception {
        when(builder.itemEdit(id, itemA)).thenReturn(edit(null, itemForm()));

        mvc.perform(get("/admin/catalogos/" + id + "/prendas/" + itemA))
                .andExpect(content().string(not(containsString("Guardar y siguiente"))));
    }

    @Test
    void savingAnItemPassesThePhotoRolesAndTextsToTheService() throws Exception {
        when(builder.itemEdit(id, itemA)).thenReturn(edit(itemB, itemForm()));

        mvc.perform(post("/admin/catalogos/" + id + "/prendas/" + itemA)
                .param("role_" + photo1, "main").param("role_" + photo2, "extra")
                .param("highlightText", "Mi frase").param("detailsText", "Mi ficha"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id + "#prenda-" + itemA));

        ArgumentCaptor<ItemForm> captor = ArgumentCaptor.forClass(ItemForm.class);
        verify(builder).saveItem(eq(id), eq(itemA), captor.capture());
        assertThat(captor.getValue().getRoles()).containsEntry(photo1.toString(), "main").containsEntry(photo2.toString(), "extra");
        assertThat(captor.getValue().getHighlightText()).isEqualTo("Mi frase");
        assertThat(captor.getValue().getDetailsText()).isEqualTo("Mi ficha");
    }

    @Test
    void saveAndNextOpensTheNextItem() throws Exception {
        when(builder.itemEdit(id, itemA)).thenReturn(edit(itemB, itemForm()));

        mvc.perform(post("/admin/catalogos/" + id + "/prendas/" + itemA).param("role_" + photo1, "main").param("next", "true"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id + "/prendas/" + itemB));
    }

    @Test
    void aRuleBrokenShowsTheMessageAndKeepsWhatWasTyped() throws Exception {
        doThrow(new BuilderException("photos", "brochures.item.photos.extras")).when(builder).saveItem(eq(id), eq(itemA), any());
        when(builder.itemEdit(eq(id), eq(itemA), any())).thenReturn(edit(itemB, itemForm()));

        mvc.perform(post("/admin/catalogos/" + id + "/prendas/" + itemA).param("role_" + photo1, "extra")
                .param("highlightText", "Mi frase"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elige como máximo 2 fotos de apoyo")));
    }

    @Test
    void resettingATextRefillsItWithoutSaving() throws Exception {
        when(builder.itemEdit(eq(id), eq(itemA), any())).thenReturn(edit(itemB, itemForm()));

        mvc.perform(post("/admin/catalogos/" + id + "/prendas/" + itemA).param("reset", "highlight"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aún no se guardó")));

        verify(builder).resetText(eq(id), eq(itemA), any(), eq("highlight"));
        verify(builder, never()).saveItem(any(), any(), any());
    }

    @Test
    void theMenuHighlightsCatalogs() throws Exception {
        when(builder.list()).thenReturn(List.of());

        mvc.perform(get("/admin/catalogos"))
                .andExpect(content().string(containsString("is-active")))
                .andExpect(content().string(containsString("href=\"/admin/catalogos\"")));
    }

    // ---------------- PDF ----------------

    private BrochureBuilderService.PdfCatalog catalog(int items) {
        List<BrochureBuilderService.PdfItem> list = new java.util.ArrayList<>();
        for (int i = 0; i < items; i++) {
            list.add(new BrochureBuilderService.PdfItem("SPK-00" + i, "Prenda " + i, "Chompas", "Disponible", false,
                    java.math.BigDecimal.TEN, java.math.BigDecimal.TEN, 0, "http://img/" + i, List.of(), List.of("Frase"), List.of("Detalle")));
        }
        return new BrochureBuilderService.PdfCatalog("Catálogo Verano 2026", "Hola", true, list);
    }

    @Test
    void thePdfOpensInTheBrowserWithAFriendlyFileName() throws Exception {
        when(builder.pdfData(id)).thenReturn(catalog(2));
        when(pdf.renderCatalog(any(), any(), any(), any())).thenReturn("%PDF-1.7 prueba".getBytes());

        mvc.perform(get("/admin/catalogos/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("inline")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("catalogo-verano-2026.pdf")));
    }

    @Test
    void thePdfCanBeDownloaded() throws Exception {
        when(builder.pdfData(id)).thenReturn(catalog(1));
        when(pdf.renderCatalog(any(), any(), any(), any())).thenReturn("%PDF-1.7".getBytes());

        mvc.perform(get("/admin/catalogos/" + id + "/pdf").param("descargar", "true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void thePdfNeedsAtLeastOnePiece() throws Exception {
        when(builder.pdfData(id)).thenReturn(catalog(0));

        mvc.perform(get("/admin/catalogos/" + id + "/pdf"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id))
                .andExpect(flash().attribute("flashError", "Agrega al menos una prenda para poder armar el PDF."));

        verify(pdf, never()).renderCatalog(any(), any(), any(), any());
    }

    @Test
    void aFailureBuildingThePdfComesBackWithAMessage() throws Exception {
        when(builder.pdfData(id)).thenReturn(catalog(1));
        when(pdf.renderCatalog(any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        mvc.perform(get("/admin/catalogos/" + id + "/pdf"))
                .andExpect(redirectedUrl("/admin/catalogos/" + id))
                .andExpect(flash().attributeExists("flashError"));
    }

    @Test
    void theOverviewOffersTheTwoPdfButtons() throws Exception {
        mvc.perform(get("/admin/catalogos/" + id))
                .andExpect(content().string(containsString("Ver PDF")))
                .andExpect(content().string(containsString("Descargar PDF")))
                .andExpect(content().string(containsString("/admin/catalogos/" + id + "/pdf")))
                .andExpect(content().string(containsString("descargar=true")));
    }

    @Test
    void fileNamesAreCleanedUp() {
        assertThat(BrochureBuilderController.fileName("Catálogo Verano 2026")).isEqualTo("catalogo-verano-2026.pdf");
        assertThat(BrochureBuilderController.fileName("  ¡Navidad & Año Nuevo!  ")).isEqualTo("navidad-ano-nuevo.pdf");
        assertThat(BrochureBuilderController.fileName("???")).isEqualTo("catalogo.pdf");
    }
}
