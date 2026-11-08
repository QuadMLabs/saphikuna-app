package com.quadmlabs.saphikuna.brochure;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.PdfCatalog;
import com.quadmlabs.saphikuna.brochure.builder.BrochureBuilderService.PdfItem;
import com.quadmlabs.saphikuna.dto.StoreInfoForm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.List;

/**
 * Genera el PDF del catálogo armado en el panel: arma una página XHTML con Thymeleaf (plantilla templates/pdf/) y
 * la convierte con OpenHTMLtoPDF. La plantilla usa tablas y no flex/grid porque el conversor
 * solo entiende CSS 2.1 y parte de CSS 3.
 */
@Service
public class BrochurePdfService {

    private static final Logger log = LoggerFactory.getLogger(BrochurePdfService.class);

    private final BrochureImageService images;
    private final SpringTemplateEngine engine;

    public BrochurePdfService(BrochureImageService images, MessageSource messages) {
        this.images = images;

        // Motor propio (NO es un bean): el de las pantallas del panel no se toca
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".xhtml");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        this.engine = new SpringTemplateEngine();
        this.engine.setTemplateResolver(resolver);
        this.engine.setTemplateEngineMessageSource(messages);
    }

    /** Un PDF a la vez: generarlo usa mucha memoria y el servidor es pequeño. */
    private final java.util.concurrent.Semaphore rendering = new java.util.concurrent.Semaphore(1, true);

    // ------------------------------------------------------------------
    // Catálogo armado en el panel (nombre, texto de inicio, prendas con sus fotos y textos)
    // ------------------------------------------------------------------

    /** Una foto por prenda en la portada, como máximo. */
    private static final int COVER_PHOTOS = 3;

    /** El catálogo armado en PDF. {@code storeUrl} puede ser vacío (entonces no se muestra). */
    public byte[] renderCatalog(PdfCatalog catalog, StoreInfoForm store, String storeUrl, LocalDate today) {
        try {
            rendering.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la generación del PDF", e);
        }
        try {
            long started = System.nanoTime();

            List<String> mains = catalog.items().stream().map(PdfItem::mainUrl).filter(u -> u != null && !u.isBlank()).toList();
            List<String> extras = catalog.items().stream().flatMap(i -> i.extraUrls().stream()).toList();

            Context context = new Context(LocaleContextHolder.getLocale());
            context.setVariable("catalog", catalog);
            context.setVariable("store", store);
            context.setVariable("storeUrl", displayUrl(storeUrl));
            context.setVariable("shopUrl", shopBase(storeUrl));
            context.setVariable("monthYear", monthYear(today));
            context.setVariable("mains", images.prepare(mains, BrochureImageService.Shape.HERO));
            context.setVariable("extras", images.prepare(extras, BrochureImageService.Shape.SUPPORT));
            // Portada: versiones pequeñas (así las fotos no se guardan dos veces en grande)
            context.setVariable("cover", images.prepare(mains.stream().limit(COVER_PHOTOS).toList(), BrochureImageService.Shape.SUPPORT));
            context.setVariable("coverUrls", mains.stream().limit(COVER_PHOTOS).toList());
            context.setVariable("logo", images.logoDataUri(520));
            context.setVariable("introPage", catalog.hasIntro() ? 1 : 0);

            return toPdf(engine.process("catalog", context), catalog.items().size(), started);
        } finally {
            rendering.release();
        }
    }

    /** "saphikuna.netlify.app" a partir de "https://saphikuna.netlify.app/". */
    static String displayUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.trim().replaceFirst("^https?://", "").replaceFirst("/+$", "");
    }

    /** Dirección de la tienda con "https://" y terminada en "/"; vacía si no hay. */
    static String shopBase(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String clean = url.trim();
        if (!clean.matches("^https?://.*")) {
            clean = "https://" + clean;
        }
        return clean.endsWith("/") ? clean : clean + "/";
    }

    /** "Noviembre 2026". */
    static String monthYear(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.FULL, new Locale("es"));
        return month.substring(0, 1).toUpperCase(new Locale("es")) + month.substring(1) + " " + date.getYear();
    }

    private byte[] toPdf(String xhtml, int products, long started) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> font("PlayfairDisplay-Regular.ttf"), "Playfair Display", 400,
                    PdfRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> font("PlayfairDisplay-Bold.ttf"), "Playfair Display", 700,
                    PdfRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();

            byte[] pdf = out.toByteArray();
            log.info("Catálogo PDF generado: {} productos, {} KB, {} ms",
                    products, pdf.length / 1024, (System.nanoTime() - started) / 1_000_000);
            return pdf;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del catálogo", e);
        }
    }

    private static InputStream font(String name) {
        return BrochurePdfService.class.getResourceAsStream("/static/fonts/" + name);
    }

}
