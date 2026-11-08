package com.quadmlabs.saphikuna.brochure;

import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.PdfCatalog;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.PdfItem;
import com.quadmlabs.saphikuna.dto.StoreInfoForm;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El PDF del catálogo armado: cuántas páginas tiene y qué dice (las fotos se simulan: sin red). */
class CatalogPdfTest {

    private BrochurePdfService pdf;
    private StoreInfoForm store;

    @BeforeEach
    void setUp() {
        BrochureImageService images = mock(BrochureImageService.class);
        when(images.prepare(any(), any())).thenReturn(Map.of()); // sin fotos: cada ficha muestra "Sin foto"
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        pdf = new BrochurePdfService(images, messages);

        store = new StoreInfoForm();
        store.setStoreName("Saphikuna");
        store.setTagline("Tejiendo sueños, vistiendo raíces");
        store.setWhatsapp("+593 98 266 7911");
    }

    private static PdfItem item(String code, String name, String price, int discount, List<String> extras) {
        return new PdfItem(code, name, "Chompas", "Disponible", false, new BigDecimal(price),
                new BigDecimal(discount == 0 ? price : "40.00"), discount, "http://img/" + code, extras,
                List.of("Clásico y elegante."), List.of("Material: Hilo angora", "Tallas: S y M"));
    }

    /** Sin espacios: el texto con letras espaciadas se extrae como "N O V I E M B R E". */
    private static String squash(String text) {
        return text.replaceAll("\s+", "");
    }

    private String[] pages(PdfCatalog catalog) throws Exception {
        byte[] bytes = pdf.renderCatalog(catalog, store, "https://saphikuna.netlify.app/", LocalDate.of(2026, 11, 8));
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String[] out = new String[doc.getNumberOfPages()];
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 0; i < out.length; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                out[i] = stripper.getText(doc);
            }
            return out;
        }
    }

    @Test
    void hasACoverAnIntroOnePagePerPieceAndAClosingPage() throws Exception {
        PdfCatalog catalog = new PdfCatalog("Catálogo Verano 2026", "Calidad y estilo", true, List.of(
                item("SPK-001", "Suéter Coral", "50.00", 20, List.of("http://img/e1", "http://img/e2")),
                item("SPK-002", "Blusa Gaia", "31.00", 0, List.of())));

        String[] pages = pages(catalog);

        assertThat(pages).hasSize(1 + 1 + 2 + 1);
        assertThat(pages[0]).contains("Catálogo Verano 2026").contains("Tejiendo sueños");
        assertThat(squash(pages[0])).containsIgnoringCase("noviembre2026"); // la fecha va con letras espaciadas
        assertThat(pages[1]).contains("Calidad y estilo");
        assertThat(squash(pages[2])).contains("SPK-001");
        assertThat(pages[2]).contains("Suéter Coral").contains("Clásico y elegante.");
        assertThat(squash(pages[3])).contains("SPK-002");
        assertThat(pages[3]).contains("Blusa Gaia");
        assertThat(pages[4]).contains("+593 98 266 7911").contains("saphikuna.netlify.app");
    }

    @Test
    void withoutAnIntroTextThereIsNoIntroPage() throws Exception {
        PdfCatalog catalog = new PdfCatalog("Solo uno", null, true, List.of(item("SPK-001", "Suéter Coral", "50.00", 0, List.of())));

        assertThat(pages(catalog)).hasSize(1 + 1 + 1);
    }

    @Test
    void pricesAppearOnlyWhenTheCatalogShowsThem() throws Exception {
        PdfItem it = item("SPK-001", "Suéter Coral", "50.00", 20, List.of());

        String withPrices = String.join("\n", pages(new PdfCatalog("A", null, true, List.of(it))));
        String without = String.join("\n", pages(new PdfCatalog("A", null, false, List.of(it))));

        assertThat(withPrices).contains("$40.00").contains("$50.00").contains("-20%");
        assertThat(without).doesNotContain("$40.00").doesNotContain("$50.00");
    }

    @Test
    void theDetailLinesAndAvailabilityLabelsAreShown() throws Exception {
        PdfItem onRequest = new PdfItem("SPK-009", "Gorro", "Accesorios", "Bajo pedido", true, new BigDecimal("15.00"),
                new BigDecimal("15.00"), 0, null, List.of(), List.of("Para el frío."), List.of("Color: Vino"));

        String text = String.join("\n", pages(new PdfCatalog("A", null, true, List.of(onRequest))));

        assertThat(text).contains("Color: Vino").contains("Sin foto");
        assertThat(squash(text)).contains("BAJOPEDIDO").contains("NUEVO"); // las etiquetas van con letras espaciadas
    }

    /** Las direcciones de los enlaces que tiene cada página del PDF. */
    private List<List<String>> links(PdfCatalog catalog, String storeUrl) throws Exception {
        byte[] bytes = pdf.renderCatalog(catalog, store, storeUrl, LocalDate.of(2026, 11, 8));
        List<List<String>> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            for (var page : doc.getPages()) {
                List<String> uris = new ArrayList<>();
                for (var annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action) {
                        uris.add(action.getURI());
                    }
                }
                out.add(uris);
            }
        }
        return out;
    }

    @Test
    void eachPieceLinksToItsPageInTheStore() throws Exception {
        PdfCatalog catalog = new PdfCatalog("A", "Hola", true, List.of(
                item("SPK-001", "Suéter Coral", "50.00", 0, List.of()),
                item("SPK-002", "Blusa Gaia", "31.00", 0, List.of())));

        List<List<String>> links = links(catalog, "https://saphikuna.netlify.app/");

        // portada, inicio, 2 prendas, cierre
        assertThat(links).hasSize(5);
        assertThat(links.get(2)).containsExactly("https://saphikuna.netlify.app/product.html?code=SPK-001");
        assertThat(links.get(3)).containsExactly("https://saphikuna.netlify.app/product.html?code=SPK-002");
        assertThat(links.get(0)).isEmpty();
        assertThat(links.get(4)).isEmpty();
    }

    @Test
    void withoutAStoreAddressThereAreNoLinks() throws Exception {
        PdfCatalog catalog = new PdfCatalog("A", null, true, List.of(item("SPK-001", "Suéter Coral", "50.00", 0, List.of())));

        assertThat(links(catalog, "")).allSatisfy(page -> assertThat(page).isEmpty());
    }

    @Test
    void theStoreAddressIsNormalised() {
        assertThat(BrochurePdfService.shopBase("saphikuna.netlify.app")).isEqualTo("https://saphikuna.netlify.app/");
        assertThat(BrochurePdfService.shopBase(" https://saphikuna.netlify.app ")).isEqualTo("https://saphikuna.netlify.app/");
        assertThat(BrochurePdfService.shopBase("")).isEmpty();
        assertThat(BrochurePdfService.shopBase(null)).isEmpty();
    }

    @Test
    void displayHelpersReadWell() {
        assertThat(BrochurePdfService.displayUrl("https://saphikuna.netlify.app/")).isEqualTo("saphikuna.netlify.app");
        assertThat(BrochurePdfService.displayUrl(null)).isEmpty();
        assertThat(BrochurePdfService.monthYear(LocalDate.of(2026, 3, 1))).isEqualTo("Marzo 2026");
    }
}
