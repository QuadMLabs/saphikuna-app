package com.quadmlabs.saphikuna.brochure;

import com.quadmlabs.saphikuna.service.CategoryService;
import com.quadmlabs.saphikuna.service.CollectionService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Catálogo: la pantalla del panel (/admin/catalogo) donde se eligen las opciones y las dos
 * salidas públicas: la página en línea (/catalogo) y el PDF (/catalogo.pdf). Las opciones viajan
 * en la URL, así cualquier enlace compartido reproduce el mismo catálogo.
 */
@Controller
public class BrochureController {

    private static final Logger log = LoggerFactory.getLogger(BrochureController.class);

    private final BrochureService brochure;
    private final BrochurePdfService pdf;
    private final CategoryService categories;
    private final CollectionService collections;

    public BrochureController(
            BrochureService brochure,
            BrochurePdfService pdf,
            CategoryService categories,
            CollectionService collections) {

        this.brochure = brochure;
        this.pdf = pdf;
        this.categories = categories;
        this.collections = collections;
    }

    // ------------------------------------------------------------------
    // Panel
    // ------------------------------------------------------------------

    @GetMapping("/admin/catalogo")
    public String admin(@ModelAttribute("options") BrochureOptions options, BindingResult ignored, HttpServletRequest request, Model model) {

        BrochureData data = brochure.build(options);

        String base = ServletUriComponentsBuilder.fromRequest(request).replacePath(request.getContextPath())
                .replaceQuery(null).build().toUriString();

        model.addAttribute("count", data.productCount());
        model.addAttribute("scope", data.scope());
        model.addAttribute("publicUrl", base + "/catalogo" + options.query());
        model.addAttribute("pdfUrl", base + "/catalogo.pdf" + options.query());
        model.addAttribute("query", options.query());
        model.addAttribute("categories", categories.findAll());
        model.addAttribute("collections", collections.findAll());
        model.addAttribute("activeMenu", "brochure");
        return "admin/brochure";
    }

    // ------------------------------------------------------------------
    // Público (sin sesión: es el enlace que se comparte con los clientes)
    // ------------------------------------------------------------------

    @GetMapping("/catalogo")
    public String online(@ModelAttribute("options") BrochureOptions options, BindingResult ignored, HttpServletRequest request, Model model) {

        BrochureData data = brochure.build(options);

        model.addAttribute("data", data);
        model.addAttribute("query", options.query());
        model.addAttribute("pageUrl", ServletUriComponentsBuilder.fromRequest(request).build().toUriString());
        return "brochure/public";
    }

    @GetMapping(value = "/catalogo.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@ModelAttribute("options") BrochureOptions options, BindingResult ignored) {

        // /catalogo.pdf es público y generarlo gasta CPU y memoria: el mismo catálogo pedido varias
        // veces seguidas se reutiliza por un minuto (también frena a quien lo pida sin parar).
        String key = options.query();
        byte[] bytes = cachedPdf(key);

        if (bytes == null) {
            BrochureData data = brochure.build(options);
            try {
                bytes = pdf.render(data);
            } catch (RuntimeException e) {
                log.error("No se pudo generar el catálogo en PDF", e);
                return ResponseEntity.internalServerError().build();
            }
            rememberPdf(key, bytes);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("catalogo-saphikuna.pdf").build().toString())
                .body(bytes);
    }

    // ------------------------------------------------------------------
    // Recuerdo breve de PDFs ya generados
    // ------------------------------------------------------------------

    private static final long PDF_TTL_MS = 60_000;
    private static final int PDF_MAX_CACHED = 5;

    private record CachedPdf(byte[] bytes, long at) {
    }

    private final java.util.Map<String, CachedPdf> pdfCache = new java.util.concurrent.ConcurrentHashMap<>();

    private byte[] cachedPdf(String key) {
        CachedPdf hit = pdfCache.get(key);
        return hit != null && System.currentTimeMillis() - hit.at() < PDF_TTL_MS ? hit.bytes() : null;
    }

    private void rememberPdf(String key, byte[] bytes) {
        long now = System.currentTimeMillis();
        pdfCache.values().removeIf(c -> now - c.at() >= PDF_TTL_MS);
        if (pdfCache.size() >= PDF_MAX_CACHED) {
            pdfCache.clear(); // pocos y de vida corta: más simple vaciar que ordenar
        }
        pdfCache.put(key, new CachedPdf(bytes, now));
    }
}
