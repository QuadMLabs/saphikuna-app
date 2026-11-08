package com.quadmlabs.saphikuna.brochure;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Genera el catálogo en PDF: arma una página XHTML con Thymeleaf (plantilla templates/pdf/) y
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

    /** El catálogo en PDF (bytes). Lanza IllegalStateException si no se pudo generar. */
    public byte[] render(BrochureData data) {
        try {
            rendering.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la generación del PDF", e);
        }
        try {
            return renderOne(data);
        } finally {
            rendering.release();
        }
    }

    private byte[] renderOne(BrochureData data) {
        long started = System.nanoTime();

        // Fotos: la principal de cada producto (vertical) y las demás (pequeñas)
        List<String> mains = new ArrayList<>();
        List<String> extras = new ArrayList<>();
        data.sections().forEach(s -> s.items().forEach(item -> {
            if (!item.photos().isEmpty()) {
                mains.add(item.photos().get(0));
                extras.addAll(item.photos().subList(1, item.photos().size()));
            }
        }));
        Map<String, String> mainImages = images.prepare(mains, BrochureImageService.Shape.PORTRAIT);
        Map<String, String> thumbImages = images.prepare(extras, BrochureImageService.Shape.SQUARE);

        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariable("data", data);
        context.setVariable("mainImages", mainImages);
        context.setVariable("thumbImages", thumbImages);
        context.setVariable("logo", images.logoDataUri());
        context.setVariable("footerText", cssSafe(data.store().getStoreName()));

        String xhtml = engine.process("brochure", context);

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
                    data.productCount(), pdf.length / 1024, (System.nanoTime() - started) / 1_000_000);
            return pdf;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del catálogo", e);
        }
    }

    private static InputStream font(String name) {
        return BrochurePdfService.class.getResourceAsStream("/static/fonts/" + name);
    }

    /** Texto que va dentro de una cadena CSS: sin comillas ni barras. */
    private static String cssSafe(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s.replace("\"", "").replace("'", "").replace("\\", ""));
    }
}
