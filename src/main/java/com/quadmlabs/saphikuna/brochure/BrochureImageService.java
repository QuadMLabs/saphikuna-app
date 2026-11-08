package com.quadmlabs.saphikuna.brochure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Prepara las fotos para el PDF: las descarga, las recorta al formato del catálogo y las
 * recodifica como JPEG liviano (data URI). Así el PDF pesa unos pocos MB en vez de decenas
 * (un PDF guarda las imágenes sin comprimir si vienen en WebP).
 */
@Service
public class BrochureImageService {

    /** Formato de la foto dentro del catálogo. */
    public enum Shape {
        /** Vertical 3:4, para la foto principal. 600x800 son más de 300 ppp en una columna de 48 mm. */
        PORTRAIT(600, 800),
        /** Cuadrada, para las fotos pequeñas (17,5 mm: 240 px bastan). */
        SQUARE(240, 240);

        final int width;
        final int height;

        Shape(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(BrochureImageService.class);

    private static final Color BACKGROUND = new Color(0xF4F1FA);
    private static final float JPEG_QUALITY = 0.82f;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Fotos ya preparadas (clave: forma + url), para no repetir descargas entre catálogos. */
    private static final int MAX_CACHED = 80;

    /** Con tope: en un servidor pequeño no puede crecer sin límite (se descartan las menos usadas). */
    private final Map<String, String> cache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHED;
                }
            });

    /** Fotos que se descargan y convierten a la vez: cada una ocupa varios MB mientras se procesa. */
    private final java.util.concurrent.Semaphore working = new java.util.concurrent.Semaphore(3);

    /**
     * Devuelve url -> data URI JPEG. Las fotos que no se puedan descargar o leer se omiten
     * (el catálogo muestra un recuadro vacío en su lugar).
     */
    public Map<String, String> prepare(Collection<String> urls, Shape shape) {

        Map<String, String> result = new HashMap<>();
        Map<String, Future<String>> pending = new HashMap<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String url : new LinkedHashSet<>(urls)) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                String key = shape.name() + "|" + url;
                String cached = cache.get(key);
                if (cached != null) {
                    result.put(url, cached);
                } else {
                    pending.put(url, pool.submit(() -> {
                        working.acquire();
                        try {
                            return fetchAndConvert(url, shape);
                        } finally {
                            working.release();
                        }
                    }));
                }
            }

            for (Map.Entry<String, Future<String>> e : pending.entrySet()) {
                try {
                    String uri = e.getValue().get(20, TimeUnit.SECONDS);
                    if (uri != null) {
                        result.put(e.getKey(), uri);
                        cache.put(shape.name() + "|" + e.getKey(), uri);
                    }
                } catch (Exception ex) {
                    log.warn("No se pudo preparar la foto {}: {}", e.getKey(), ex.getMessage());
                }
            }
        }
        return result;
    }

    /** El logo (PNG con fondo transparente) como data URI; null si no está. */
    public String logoDataUri() {
        try {
            byte[] bytes = new ClassPathResource("static/images/logo.png").getContentAsByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.warn("No se pudo leer el logo: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------

    private String fetchAndConvert(String url, Shape shape) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (source == null) {
            throw new IOException("formato de imagen no reconocido");
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(toJpeg(fit(source, shape)));
    }

    /**
     * Ajusta la foto al formato: si su proporción es parecida, recorta al centro; si es muy
     * distinta (por ejemplo una prenda muy larga), la deja entera sobre un fondo suave.
     */
    static BufferedImage fit(BufferedImage src, Shape shape) {

        double target = (double) shape.width / shape.height;
        double aspect = (double) src.getWidth() / src.getHeight();
        boolean crop = aspect >= target * 0.8 && aspect <= target * 1.25;

        BufferedImage out = new BufferedImage(shape.width, shape.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, shape.width, shape.height);

        double scale = crop
                ? Math.max((double) shape.width / src.getWidth(), (double) shape.height / src.getHeight())
                : Math.min((double) shape.width / src.getWidth(), (double) shape.height / src.getHeight());
        int w = (int) Math.round(src.getWidth() * scale);
        int h = (int) Math.round(src.getHeight() * scale);

        // por pasos para que la reducción no deje dientes de sierra
        BufferedImage scaled = src;
        while (scaled.getWidth() / 2 >= w && scaled.getHeight() / 2 >= h && scaled.getWidth() > 2) {
            scaled = resize(scaled, scaled.getWidth() / 2, scaled.getHeight() / 2);
        }
        g.drawImage(scaled, (shape.width - w) / 2, (shape.height - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage resize(BufferedImage s, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return o;
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                var imageOut = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(imageOut);
            writer.write(null, new IIOImage(image, null, null), param);
            imageOut.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
