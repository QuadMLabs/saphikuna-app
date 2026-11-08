package com.quadmlabs.saphikuna.tools;

import com.quadmlabs.saphikuna.image.WatermarkRemover;
import com.quadmlabs.saphikuna.image.WatermarkRemover.Method;
import com.quadmlabs.saphikuna.image.WatermarkRemover.Result;
import com.quadmlabs.saphikuna.service.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Herramienta de una sola vez: quita la marca de agua "Dola AI" de las fotos YA publicadas.
 * Solo se activa si se arranca con {@code --cleanup.watermark.mode=...}; en el uso normal no existe.
 *
 * <ul>
 * <li><b>preview</b>: no escribe nada en Supabase. Guarda en una carpeta un antes/después de cada foto
 * con marca, para revisarlas a ojo.</li>
 * <li><b>apply</b>: por cada foto con marca sube la versión limpia con un NOMBRE NUEVO (las fotos se
 * guardan con caché de un año, así que reemplazar el archivo no se vería), actualiza el producto y
 * anota las rutas viejas en {@code old-paths.txt}. No borra ninguna foto vieja.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "cleanup.watermark.mode")
public class WatermarkCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WatermarkCleanupRunner.class);

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final ConfigurableApplicationContext context;
    private final String mode;
    private final Path out;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WatermarkCleanupRunner(
            JdbcTemplate jdbc,
            StorageService storage,
            ConfigurableApplicationContext context,
            @Value("${cleanup.watermark.mode}") String mode,
            @Value("${cleanup.watermark.out:target/watermark-cleanup}") String out) {

        this.jdbc = jdbc;
        this.storage = storage;
        this.context = context;
        this.mode = mode;
        this.out = Path.of(out);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean apply = "apply".equalsIgnoreCase(mode);
        if (!apply && !"preview".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("cleanup.watermark.mode debe ser preview o apply");
        }
        Files.createDirectories(out);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                select i."productImagesId" as id, i."productId" as product, i."storagePath" as path,
                       p."code" as code, p."primaryImagePath" as primary_path
                from "productImages" i
                join "products" p on p."producttId" = i."productId"
                order by p."code", i."sortOrder"
                """);
        log.info("Modo {}: {} fotos en total", mode, rows.size());

        int withMark = 0;
        int cleaned = 0;
        int skipped = 0;
        int failed = 0;
        java.util.Map<String, Integer> perProduct = new java.util.HashMap<>();

        for (Map<String, Object> row : rows) {
            String path = (String) row.get("path");
            String code = (String) row.get("code");
            String key = code == null ? "sin-codigo" : code;
            String label = key + "-" + perProduct.merge(key, 1, Integer::sum);
            try {
                BufferedImage image = download(path);
                if (image == null) {
                    log.warn("{}: no se pudo leer la imagen {}", label, path);
                    failed++;
                    continue;
                }
                Result result = WatermarkRemover.analyze(image);
                if (!result.detected()) {
                    continue;
                }
                withMark++;
                log.info("{} ({}): marca detectada, puntaje {}, método {}", label, path,
                        String.format(java.util.Locale.ROOT, "%.2f", result.score()), result.method());

                if (result.method() == Method.SKIPPED) {
                    skipped++;
                    continue;
                }

                BufferedImage before = copy(image);
                WatermarkRemover.removeIfPresent(image);

                if (!apply) {
                    writePreview(label + "-" + result.method(), before, image);
                    cleaned++;
                    continue;
                }

                UUID productId = (UUID) row.get("product");
                String newPath = "products/" + productId + "/" + UUID.randomUUID() + ".webp";
                storage.replaceImage(image, newPath);
                jdbc.update("update \"productImages\" set \"storagePath\" = ? where \"productImagesId\" = ?",
                        newPath, row.get("id"));
                jdbc.update("update \"products\" set \"primaryImagePath\" = ? where \"producttId\" = ? and \"primaryImagePath\" = ?",
                        newPath, productId, path);
                Files.writeString(out.resolve("old-paths.txt"), path + " -> " + newPath + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                cleaned++;

            } catch (Exception e) {
                failed++;
                log.error("{}: falló ({})", label, e.toString());
            }
        }

        log.info("""

                ===== Resumen ({}) =====
                Fotos con marca: {}
                {}: {}
                Sin tocar (detección débil): {}
                Con error: {}
                Carpeta: {}
                """, mode, withMark, apply ? "Reemplazadas" : "Vistas previas", cleaned, skipped, failed, out.toAbsolutePath());

        System.exit(SpringApplication.exit(context));
    }

    private BufferedImage download(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(storage.getPublicUrl(path)))
                .timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            return null;
        }
        return ImageIO.read(new ByteArrayInputStream(response.body()));
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }

    /** Antes y después: la foto completa (reducida) a la izquierda y un acercamiento a la esquina debajo. */
    private void writePreview(String name, BufferedImage before, BufferedImage after) throws IOException {
        int h = 520;
        int w = before.getWidth() * h / before.getHeight();
        int zx = (int) (before.getWidth() * 0.5);
        int zy = (int) (before.getHeight() * 0.86);
        int zw = before.getWidth() - zx;
        int zh = before.getHeight() - zy;
        int zoomW = w * 2 + 10;
        int zoomH = zh * zoomW / (2 * zw) ;

        BufferedImage sheet = new BufferedImage(zoomW, h + 10 + zoomH * 2 + 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.drawImage(before, 0, 0, w, h, null);
        g.drawImage(after, w + 10, 0, w, h, null);
        int y = h + 10;
        g.drawImage(before.getSubimage(zx, zy, zw, zh), 0, y, zoomW, zoomH, null);
        g.drawImage(after.getSubimage(zx, zy, zw, zh), 0, y + zoomH + 4, zoomW, zoomH, null);
        g.dispose();
        ImageIO.write(sheet, "png", out.resolve(name + ".png").toFile());
    }
}
