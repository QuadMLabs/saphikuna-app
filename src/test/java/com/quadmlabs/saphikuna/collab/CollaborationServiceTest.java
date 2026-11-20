package com.quadmlabs.saphikuna.collab;

import com.quadmlabs.saphikuna.entity.CollabPiece;
import com.quadmlabs.saphikuna.entity.CollabPieceImage;
import com.quadmlabs.saphikuna.entity.Collaboration;
import com.quadmlabs.saphikuna.entity.Color;
import com.quadmlabs.saphikuna.repository.CollabPieceRepository;
import com.quadmlabs.saphikuna.repository.CollaborationRepository;
import com.quadmlabs.saphikuna.repository.ColorRepository;
import com.quadmlabs.saphikuna.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de las colaboraciones: marcas, prendas, colores y fotos (sin base de datos real). */
class CollaborationServiceTest {

    CollaborationRepository brands = mock(CollaborationRepository.class);
    CollabPieceRepository pieces = mock(CollabPieceRepository.class);
    ColorRepository colors = mock(ColorRepository.class);
    com.quadmlabs.saphikuna.repository.CommissionRepository commissions =
            mock(com.quadmlabs.saphikuna.repository.CommissionRepository.class);
    StorageService storage = mock(StorageService.class);
    CollaborationService service;

    UUID brandId = UUID.randomUUID();
    Collaboration brand;

    @BeforeEach
    void setUp() {
        service = new CollaborationService(brands, pieces, colors, commissions, storage,
                Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneOffset.UTC));
        brand = new Collaboration();
        brand.setCollaborationId(brandId);
        brand.setBrandName("Marca Andina");
        when(brands.findById(brandId)).thenReturn(Optional.of(brand));
        when(storage.getThumbPublicUrl(anyString())).thenAnswer(i -> "http://t/" + i.getArgument(0));
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("newImages", "a.jpg", "image/jpeg", new byte[] { 1, 2, 3 });
    }

    private static Color color(UUID id, String name) {
        Color c = new Color();
        c.setColorId(id);
        c.setName(name);
        c.setHexCode("#ffffff");
        return c;
    }

    @Test
    void creaLaMarcaConElNombreSinEspacios() {
        BrandForm form = new BrandForm();
        form.setBrandName("  Marca Nueva ");
        form.setVisible(false);
        when(brands.save(any(Collaboration.class))).thenAnswer(i -> i.getArgument(0));

        service.createBrand(form);

        ArgumentCaptor<Collaboration> saved = ArgumentCaptor.forClass(Collaboration.class);
        verify(brands).save(saved.capture());
        assertThat(saved.getValue().getBrandName()).isEqualTo("Marca Nueva");
        assertThat(saved.getValue().isVisible()).isFalse();
        assertThat(saved.getValue().getCollaborationId()).isNotNull();
    }

    @Test
    void unEncargoEntregadoSeAgregaComoPrendaSinFotosYSoloUnaVez() {
        UUID commissionId = UUID.randomUUID();
        when(pieces.existsByCommissionId(commissionId)).thenReturn(false);

        service.addDeliveredPiece(brandId, commissionId, "  40 bufandas ", "Hilo angora");

        assertThat(brand.getPieces()).hasSize(1);
        CollabPiece piece = brand.getPieces().get(0);
        assertThat(piece.getName()).isEqualTo("40 bufandas");
        assertThat(piece.getDescription()).isEqualTo("Hilo angora");
        assertThat(piece.getCommissionId()).isEqualTo(commissionId);
        assertThat(piece.getImages()).isEmpty();
        assertThat(piece.getPrimaryImagePath()).isNull();

        // si ya existe una prenda de ese encargo, no se duplica
        when(pieces.existsByCommissionId(commissionId)).thenReturn(true);
        service.addDeliveredPiece(brandId, commissionId, "40 bufandas", null);
        assertThat(brand.getPieces()).hasSize(1);
    }

    @Test
    void creaUnaPrendaConColoresFotosYLaPrimeraComoPrincipal() {
        UUID red = UUID.randomUUID();
        when(colors.findAllById(any())).thenReturn(List.of(color(red, "Rojo")));
        PieceForm form = new PieceForm();
        form.setName("  Chompa  ");
        form.setDescription("   ");
        form.setColorIds(List.of(red));
        form.setNewImages(List.of(photo(), photo()));

        service.createPiece(brandId, form);

        assertThat(brand.getPieces()).hasSize(1);
        CollabPiece piece = brand.getPieces().get(0);
        assertThat(piece.getName()).isEqualTo("Chompa");
        assertThat(piece.getDescription()).isNull();
        assertThat(piece.getColors()).extracting(Color::getName).containsExactly("Rojo");
        assertThat(piece.getImages()).hasSize(2);
        assertThat(piece.getPrimaryImagePath()).isEqualTo(piece.getImages().get(0).getStoragePath());
        assertThat(piece.getImages().get(0).getStoragePath()).startsWith("collaborations/" + piece.getCollabPieceId());
        verify(storage, org.mockito.Mockito.times(2)).upload(any(), anyString());
    }

    @Test
    void laFotoPrincipalElegidaEntreLasNuevasSeRespeta() {
        PieceForm form = new PieceForm();
        form.setName("Chompa");
        form.setNewImages(List.of(photo(), photo()));
        form.setPrimary("new:1");

        service.createPiece(brandId, form);

        CollabPiece piece = brand.getPieces().get(0);
        assertThat(piece.getPrimaryImagePath()).isEqualTo(piece.getImages().get(1).getStoragePath());
    }

    @Test
    void noPermiteMasDelMaximoDeFotos() {
        PieceForm form = new PieceForm();
        form.setName("Chompa");
        form.setNewImages(java.util.stream.IntStream.range(0, PieceForm.MAX_IMAGES + 1)
                .mapToObj(i -> (org.springframework.web.multipart.MultipartFile) photo()).toList());

        assertThatThrownBy(() -> service.createPiece(brandId, form))
                .isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).upload(any(), anyString());
    }

    @Test
    void rechazaUnColorQueNoExiste() {
        when(colors.findAllById(any())).thenReturn(List.of());
        PieceForm form = new PieceForm();
        form.setName("Chompa");
        form.setColorIds(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.createPiece(brandId, form))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Color");
    }

    @Test
    void alEditarSeQuitanLasFotosMarcadasYSeBorranDelStorage() {
        CollabPiece piece = existingPiece("a", "b");
        CollabPieceImage first = piece.getImages().get(0);

        PieceForm form = new PieceForm();
        form.setName("Chompa");
        PieceForm.ImageRow row = new PieceForm.ImageRow();
        row.setId(first.getCollabPieceImageId());
        row.setDelete(true);
        form.setImages(new java.util.ArrayList<>(List.of(row)));

        service.updatePiece(piece.getCollabPieceId(), form);

        assertThat(piece.getImages()).hasSize(1);
        assertThat(piece.getImages().get(0).getStoragePath()).isEqualTo("collaborations/x/b.webp");
        // la principal era la quitada: pasa a la que queda
        assertThat(piece.getPrimaryImagePath()).isEqualTo("collaborations/x/b.webp");
        verify(storage).delete("collaborations/x/a.webp");
        verify(pieces).save(piece);
    }

    @Test
    void alBorrarLaMarcaSeBorranLasFotosDeTodasSusPrendas() {
        CollabPiece one = existingPiece("a", "b");
        CollabPiece two = existingPiece("c");
        brand.getPieces().addAll(List.of(one, two));

        service.deleteBrand(brandId);

        verify(brands).delete(brand);
        verify(storage).delete("collaborations/x/a.webp");
        verify(storage).delete("collaborations/x/b.webp");
        verify(storage).delete("collaborations/x/c.webp");
    }

    @Test
    void alBorrarUnaPrendaSeBorranSusFotos() {
        CollabPiece piece = existingPiece("a");
        brand.getPieces().add(piece);

        service.deletePiece(piece.getCollabPieceId());

        assertThat(brand.getPieces()).isEmpty();
        verify(storage).delete("collaborations/x/a.webp");
    }

    @Test
    void laListaTraLasMarcasConSusPrendasYMiniaturas() {
        CollabPiece piece = existingPiece("a", "b");
        piece.setName("Chompa");
        piece.getColors().add(color(UUID.randomUUID(), "Rojo"));
        brand.getPieces().add(piece);
        when(brands.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(brand));

        List<CollaborationService.BrandRow> rows = service.brands();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).name()).isEqualTo("Marca Andina");
        CollaborationService.PieceRow row = rows.get(0).pieces().get(0);
        assertThat(row.name()).isEqualTo("Chompa");
        assertThat(row.photoCount()).isEqualTo(2);
        assertThat(row.colors()).extracting(CollaborationService.ColorDot::name).containsExactly("Rojo");
        assertThat(row.thumbUrl()).isEqualTo("http://t/collaborations/x/a.webp");
    }

    /** Una prenda ya guardada, con una foto por cada nombre dado; la principal es la primera. */
    private CollabPiece existingPiece(String... names) {
        CollabPiece piece = new CollabPiece();
        piece.setCollabPieceId(UUID.randomUUID());
        piece.setCollaboration(brand);
        piece.setName("Chompa");
        short order = 0;
        for (String n : names) {
            CollabPieceImage img = new CollabPieceImage();
            img.setCollabPieceImageId(UUID.randomUUID());
            img.setPiece(piece);
            img.setStoragePath("collaborations/x/" + n + ".webp");
            img.setSortOrder(order++);
            piece.getImages().add(img);
        }
        piece.setPrimaryImagePath(piece.getImages().get(0).getStoragePath());
        when(pieces.findById(piece.getCollabPieceId())).thenReturn(Optional.of(piece));
        return piece;
    }
}
