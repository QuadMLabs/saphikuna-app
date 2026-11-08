package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.image.WatermarkRemover;
import com.quadmlabs.saphikuna.service.StorageService;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/**
 * Almacena imágenes en Supabase Storage (plan free: 1 GB).
 * Cada imagen se redimensiona y se comprime a WebP hasta quedar bajo
 * {@code supabase.storage.max-bytes}; además se genera una miniatura.
 */
@Service("storageService")
public class StorageServiceImpl implements StorageService {

        private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

        private static final float MIN_QUALITY = 0.65f;
        private static final int MAX_ATTEMPTS = 8;

        private final RestClient restClient = RestClient.builder().build();

        private final String supabaseUrl;
        private final String serviceKey;
        private final String bucket;

        private final int maxWidth;
        private final int maxHeight;
        private final long maxOriginalSize;
        private final float webpQuality;
        private final int maxBytes;
        private final int thumbSize;

        /** Quitar la marca de agua "Dola AI" al subir fotos (app.images.remove-watermark, por defecto sí). */
        @Value("${app.images.remove-watermark:true}")
        private boolean removeWatermark = true;

        public StorageServiceImpl(
                        @Value("${supabase.url}") String supabaseUrl,
                        @Value("${supabase.service-key}") String serviceKey,
                        @Value("${supabase.storage.bucket}") String bucket,
                        @Value("${supabase.storage.max-width}") int maxWidth,
                        @Value("${supabase.storage.max-height}") int maxHeight,
                        @Value("${supabase.storage.max-original-size}") long maxOriginalSize,
                        @Value("${supabase.storage.webp-quality}") float webpQuality,
                        @Value("${supabase.storage.max-bytes:61440}") int maxBytes,
                        @Value("${supabase.storage.thumb-size:240}") int thumbSize) {

                this.supabaseUrl = supabaseUrl;
                this.serviceKey = serviceKey;
                this.bucket = bucket;
                this.maxWidth = maxWidth;
                this.maxHeight = maxHeight;
                this.maxOriginalSize = maxOriginalSize;
                this.webpQuality = webpQuality;
                this.maxBytes = maxBytes;
                this.thumbSize = thumbSize;
        }

        @Override
        public String upload(MultipartFile file, String path) {

                if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("El archivo está vacío");
                }

                if (path == null || path.isBlank()) {
                        throw new IllegalArgumentException("La ruta del archivo es obligatoria");
                }

                if (file.getSize() > maxOriginalSize) {
                        throw new IllegalArgumentException("La imagen supera el tamaño máximo permitido");
                }

                try {
                        Processed processed = process(file.getBytes());
                        byte[] main = processed.main();
                        byte[] thumb = processed.thumb();

                        send(path, main);
                        try {
                                send(thumbPath(path), thumb);
                        } catch (RuntimeException e) {
                                delete(path);
                                throw e;
                        }

                        log.info("Imagen {} subida: {} KB (miniatura {} KB)",
                                        path, main.length / 1024, thumb.length / 1024);

                        return getPublicUrl(path);

                } catch (IOException e) {
                        throw new IllegalStateException("No fue posible procesar la imagen", e);
                }
        }

        @Override
        public void replaceImage(BufferedImage image, String newPath) {
                try {
                        byte[] main = encodeUnderLimit(image, maxWidth, maxHeight, maxBytes);
                        byte[] thumb = encodeUnderLimit(image, thumbSize, thumbSize, maxBytes / 4);

                        send(newPath, main);
                        try {
                                send(thumbPath(newPath), thumb);
                        } catch (RuntimeException e) {
                                delete(newPath);
                                throw e;
                        }
                        log.info("Imagen {} reemplazada: {} KB (miniatura {} KB)", newPath, main.length / 1024, thumb.length / 1024);
                } catch (IOException e) {
                        throw new IllegalStateException("No fue posible procesar la imagen", e);
                }
        }

        /** Cliente aparte con tiempos límite cortos: la pantalla de estado no debe quedarse esperando. */
        private final RestClient pingClient = RestClient.builder()
                        .requestFactory(pingFactory())
                        .build();

        private static org.springframework.http.client.JdkClientHttpRequestFactory pingFactory() {
                var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                                java.net.http.HttpClient.newBuilder()
                                                .connectTimeout(java.time.Duration.ofSeconds(3)).build());
                factory.setReadTimeout(java.time.Duration.ofSeconds(3));
                return factory;
        }

        @Override
        public boolean ping() {
                try {
                        pingClient.get()
                                        .uri(supabaseUrl + "/storage/v1/bucket/" + bucket)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                                        .header("apikey", serviceKey)
                                        .retrieve()
                                        .toBodilessEntity();
                        return true;
                } catch (RuntimeException e) {
                        log.warn("Supabase Storage no responde: {}", e.getMessage());
                        return false;
                }
        }

        /** Resultado de optimizar una foto: imagen principal y miniatura, ambas WebP. */
        record Processed(byte[] main, byte[] thumb) {
        }

        /**
         * Lee la foto, aplica la rotación del EXIF (celulares), y genera la imagen
         * principal y la miniatura. No toca la red: se puede probar sola.
         */
        Processed process(byte[] raw) throws IOException {

                // Una foto de celular decodificada ocupa decenas de MB: de a una por vez, para que dos
                // personas subiendo fotos a la vez no agoten la memoria en un servidor pequeño.
                try {
                        processing.acquire();
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Se interrumpió el procesamiento de la imagen", e);
                }
                try {
                        return processOne(raw);
                } finally {
                        processing.release();
                }
        }

        /** Fotos que se procesan a la vez (ver {@link #process}). */
        private final java.util.concurrent.Semaphore processing = new java.util.concurrent.Semaphore(1, true);

        private Processed processOne(byte[] raw) throws IOException {

                BufferedImage original = ImageIO.read(new java.io.ByteArrayInputStream(raw));

                if (original == null) {
                        throw new IllegalArgumentException("El archivo no es una imagen válida");
                }

                // Sin esto, una foto vertical del celular queda girada 90° en la tienda
                original = ImageOrientation.apply(original, ImageOrientation.read(raw));

                // Las fotos de modelo generadas con IA traen la marca "Dola AI" abajo a la derecha
                if (removeWatermark) {
                        WatermarkRemover.Result mark = WatermarkRemover.removeIfPresent(original);
                        if (mark.detected()) {
                                log.info("Marca de agua {} (puntaje {}, {})",
                                                mark.removed() ? "quitada" : "detectada pero sin tocar",
                                                String.format(java.util.Locale.ROOT, "%.2f", mark.score()), mark.method());
                        }
                }

                return new Processed(
                                encodeUnderLimit(original, maxWidth, maxHeight, maxBytes),
                                encodeUnderLimit(original, thumbSize, thumbSize, maxBytes / 4));
        }

        /**
         * Redimensiona y comprime; si supera {@code limit}, baja la calidad y luego
         * las dimensiones hasta cumplirlo (o agotar los intentos).
         */
        private byte[] encodeUnderLimit(BufferedImage original, int boxW, int boxH, int limit)
                        throws IOException {

                float quality = webpQuality;
                double scale = fitScale(original, boxW, boxH);
                byte[] bytes = null;

                for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                        bytes = toWebp(scaled(original, scale), quality);

                        if (bytes.length <= limit) {
                                return bytes;
                        }

                        if (quality - 0.10f >= MIN_QUALITY) {
                                quality -= 0.10f;
                        } else {
                                scale *= 0.90;
                        }
                }

                return bytes;
        }

        private double fitScale(BufferedImage image, int boxW, int boxH) {
                return Math.min(1.0, Math.min(
                                (double) boxW / image.getWidth(),
                                (double) boxH / image.getHeight()));
        }

        /** Siempre devuelve RGB (fondo blanco) para evitar problemas con transparencias. */
        private BufferedImage scaled(BufferedImage source, double scale) {

                int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

                BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = target.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.drawImage(source, 0, 0, w, h, null);
                g.dispose();

                return target;
        }

        private byte[] toWebp(BufferedImage image, float quality) throws IOException {

                var writers = ImageIO.getImageWritersByFormatName("webp");

                if (!writers.hasNext()) {
                        throw new IllegalStateException("No hay un ImageIO writer disponible para WebP");
                }

                var writer = writers.next();

                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                        ImageWriteParam param = writer.getDefaultWriteParam();

                        if (param.canWriteCompressed()) {
                                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                                param.setCompressionType("Lossy");
                                param.setCompressionQuality(quality);
                        }

                        try (var imageOutput = ImageIO.createImageOutputStream(output)) {
                                writer.setOutput(imageOutput);
                                writer.write(null, new IIOImage(image, null, null), param);
                        }

                        return output.toByteArray();

                } finally {
                        writer.dispose();
                }
        }

        private void send(String path, byte[] bytes) {
                restClient.post()
                                .uri(objectUrl(path))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                                .header("apikey", serviceKey)
                                .header(HttpHeaders.CONTENT_TYPE, "image/webp")
                                // Cache de 1 año: reduce el egress (el free tiene cupo limitado)
                                .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                                .body(bytes)
                                .retrieve()
                                .toBodilessEntity();
        }

        private String objectUrl(String path) {
                return supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;
        }

        @Override
        public String getPublicUrl(String path) {

                if (path == null || path.isBlank()) {
                        return null;
                }

                return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + path;
        }

        @Override
        public void delete(String path) {

                if (path == null || path.isBlank()) {
                        return;
                }

                for (String p : new String[] { path, thumbPath(path) }) {
                        try {
                                restClient.delete()
                                                .uri(objectUrl(p))
                                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceKey)
                                                .header("apikey", serviceKey)
                                                .retrieve()
                                                .toBodilessEntity();
                        } catch (RuntimeException e) {
                                log.warn("No se pudo borrar {} del storage: {}", p, e.getMessage());
                        }
                }
        }
}
