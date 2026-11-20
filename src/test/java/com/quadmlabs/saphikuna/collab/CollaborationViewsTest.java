package com.quadmlabs.saphikuna.collab;

import com.quadmlabs.saphikuna.controller.PermissiveSecurity;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.service.ColorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pantallas de colaboraciones: lista, marca y prenda. */
@WebMvcTest(CollaborationController.class)
@Import(PermissiveSecurity.class)
class CollaborationViewsTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CollaborationService service;
    @MockitoBean
    ColorService colorService;

    private final UUID brandId = UUID.randomUUID();
    private final UUID pieceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Color red = new Color();
        red.setColorId(UUID.randomUUID());
        red.setName("Rojo");
        red.setHexCode("#ff0000");
        when(colorService.findAll()).thenReturn(List.of(red));
        when(service.brandName(brandId)).thenReturn("Marca Andina");
        when(service.brandOfPiece(pieceId)).thenReturn(brandId);
    }

    @Test
    void laListaMuestraLasMarcasConSusPrendas() throws Exception {
        var piece = new CollaborationService.PieceRow(pieceId, "Chompa con capucha", "http://t/1",
                List.of(new CollaborationService.ColorDot("Rojo", "#ff0000")), 3);
        when(service.brands()).thenReturn(List.of(
                new CollaborationService.BrandRow(brandId, "Marca Andina", true, List.of(piece)),
                new CollaborationService.BrandRow(UUID.randomUUID(), "Marca Oculta", false, List.of())));

        mvc.perform(get("/admin/colaboraciones"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Marca Andina")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chompa con capucha")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Oculta en la tienda")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/colaboraciones/" + brandId + "/prendas/nueva")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Esta marca aún no tiene prendas")));
    }

    @Test
    void laListaVaciaInvitaAAgregarLaPrimeraMarca() throws Exception {
        when(service.brands()).thenReturn(List.of());

        mvc.perform(get("/admin/colaboraciones"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Todavía no hay colaboraciones")));
    }

    @Test
    void creaUnaMarca() throws Exception {
        mvc.perform(post("/admin/colaboraciones").param("brandName", "Marca Nueva").param("visible", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/colaboraciones"))
                .andExpect(flash().attribute("flashMessage", "collab.brand.saved"));
        verify(service).createBrand(any(BrandForm.class));
    }

    @Test
    void laMarcaSinNombreNoSeGuarda() throws Exception {
        mvc.perform(post("/admin/colaboraciones").param("brandName", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Escribe el nombre de la marca")));
        verify(service, never()).createBrand(any());
    }

    @Test
    void elFormularioDeEditarMarcaTraElNombre() throws Exception {
        BrandForm form = new BrandForm();
        form.setBrandName("Marca Andina");
        when(service.brandForm(brandId)).thenReturn(form);

        mvc.perform(get("/admin/colaboraciones/" + brandId + "/editar"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Marca Andina\"")));
    }

    @Test
    void elFormularioDePrendaNuevaMuestraFotosNombreYColores() throws Exception {
        mvc.perform(get("/admin/colaboraciones/" + brandId + "/prendas/nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prenda hecha para Marca Andina")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"newImages\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-max=\"6\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"colorIds\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rojo")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Precio"))));
    }

    @Test
    void guardaUnaPrendaNuevaConFoto() throws Exception {
        mvc.perform(multipart("/admin/colaboraciones/" + brandId + "/prendas")
                .file(new MockMultipartFile("newImages", "a.jpg", "image/jpeg", new byte[] { 1 }))
                .param("name", "Chompa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/colaboraciones"))
                .andExpect(flash().attribute("flashMessage", "collab.piece.saved"));
        verify(service).createPiece(any(UUID.class), any(PieceForm.class));
    }

    @Test
    void rechazaArchivosQueNoSonImagenes() throws Exception {
        mvc.perform(multipart("/admin/colaboraciones/" + brandId + "/prendas")
                .file(new MockMultipartFile("newImages", "a.pdf", "application/pdf", new byte[] { 1 }))
                .param("name", "Chompa"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Solo se pueden subir imágenes")));
        verify(service, never()).createPiece(any(), any());
    }

    @Test
    void rechazaMasDeSeisFotos() throws Exception {
        var request = multipart("/admin/colaboraciones/" + brandId + "/prendas").param("name", "Chompa");
        for (int i = 0; i < 7; i++) {
            request.file(new MockMultipartFile("newImages", "a" + i + ".jpg", "image/jpeg", new byte[] { 1 }));
        }
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Solo se permiten 6 fotos")));
        verify(service, never()).createPiece(any(), any());
    }

    @Test
    void editarUnaPrendaMuestraSusFotos() throws Exception {
        PieceForm form = new PieceForm();
        form.setName("Chompa con capucha");
        PieceForm.ImageRow row = new PieceForm.ImageRow();
        row.setId(UUID.randomUUID());
        row.setThumbUrl("http://t/foto1");
        form.setImages(new java.util.ArrayList<>(List.of(row)));
        form.setPrimary("existing:" + row.getId());
        when(service.toPieceForm(pieceId)).thenReturn(form);

        mvc.perform(get("/admin/colaboraciones/prendas/" + pieceId + "/editar"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Chompa con capucha\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http://t/foto1")));
    }

    @Test
    void siFallaElGuardadoSeAvisaYSeConservaElFormulario() throws Exception {
        doThrow(new RuntimeException("boom")).when(service).updatePiece(any(UUID.class), any(PieceForm.class));

        mvc.perform(multipart("/admin/colaboraciones/prendas/" + pieceId).param("name", "Chompa"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No se pudo guardar la prenda")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"Chompa\"")));
    }

    @Test
    void eliminaPrendaYMarca() throws Exception {
        mvc.perform(post("/admin/colaboraciones/prendas/" + pieceId + "/eliminar"))
                .andExpect(redirectedUrl("/admin/colaboraciones"))
                .andExpect(flash().attribute("flashMessage", "collab.piece.deleted"));
        mvc.perform(post("/admin/colaboraciones/" + brandId + "/eliminar"))
                .andExpect(redirectedUrl("/admin/colaboraciones"))
                .andExpect(flash().attribute("flashMessage", "collab.brand.deleted"));
        verify(service).deletePiece(pieceId);
        verify(service).deleteBrand(brandId);
    }
}
