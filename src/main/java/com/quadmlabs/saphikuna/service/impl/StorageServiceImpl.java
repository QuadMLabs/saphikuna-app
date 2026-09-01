package com.quadmlabs.saphikuna.service.impl;

import com.quadmlabs.saphikuna.service.StorageService;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageServiceImpl implements StorageService {

    private final RestClient restClient;

    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucket;

    private final int maxWidth;
    private final int maxHeight;
    private final long maxOriginalSize;
    private final float webpQuality;

    public StorageServiceImpl(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${supabase.storage.bucket}") String bucket,
            @Value("${supabase.storage.max-width}") int maxWidth,
            @Value("${supabase.storage.max-height}") int maxHeight,
            @Value("${supabase.storage.max-original-size}") long maxOriginalSize,
            @Value("${supabase.storage.webp-quality}") float webpQuality) {

        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
        this.bucket = bucket;

        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxOriginalSize = maxOriginalSize;
        this.webpQuality = webpQuality;

        this.restClient = RestClient.builder().build();
    }

    @Override
    public String upload(MultipartFile file, String path) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "El archivo está vacío");
        }

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "La ruta del archivo es obligatoria");
        }

        if (file.getSize() > maxOriginalSize) {
            throw new IllegalArgumentException(
                    "La imagen supera el tamaño máximo permitido");
        }

        try {

            BufferedImage originalImage =
                    ImageIO.read(file.getInputStream());

            if (originalImage == null) {
                throw new IllegalArgumentException(
                        "El archivo no es una imagen válida");
            }

            BufferedImage processedImage =
                    resizeImage(originalImage);

            byte[] imageBytes =
                    convertToWebp(processedImage);

            String storageUrl =
                    supabaseUrl
                            + "/storage/v1/object/"
                            + bucket
                            + "/"
                            + path;

            restClient.post()
                    .uri(storageUrl)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + serviceKey)
                    .header(
                            "apikey",
                            serviceKey)
                    .header(
                            HttpHeaders.CONTENT_TYPE,
                            "image/webp")
                    .body(imageBytes)
                    .retrieve()
                    .toBodilessEntity();

            return supabaseUrl
                    + "/storage/v1/object/public/"
                    + bucket
                    + "/"
                    + path;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "No fue posible procesar la imagen",
                    e);
        }
    }

    private BufferedImage resizeImage(
            BufferedImage originalImage) {

        int originalWidth =
                originalImage.getWidth();

        int originalHeight =
                originalImage.getHeight();

        if (originalWidth <= maxWidth
                && originalHeight <= maxHeight) {

            return originalImage;
        }

        double scale = Math.min(
                (double) maxWidth / originalWidth,
                (double) maxHeight / originalHeight);

        int newWidth =
                (int) Math.round(originalWidth * scale);

        int newHeight =
                (int) Math.round(originalHeight * scale);

        BufferedImage resizedImage =
                new BufferedImage(
                        newWidth,
                        newHeight,
                        BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics =
                resizedImage.createGraphics();

        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.drawImage(
                originalImage,
                0,
                0,
                newWidth,
                newHeight,
                null);

        graphics.dispose();

        return resizedImage;
    }

    private byte[] convertToWebp(
            BufferedImage image)
            throws IOException {

        var writers =
                ImageIO.getImageWritersByFormatName("webp");

        if (!writers.hasNext()) {
            throw new IllegalStateException(
                    "No hay un ImageIO writer disponible para WebP");
        }

        var writer = writers.next();

        try (ByteArrayOutputStream output =
                new ByteArrayOutputStream()) {

            var writeParam =
                    writer.getDefaultWriteParam();

            if (writeParam.canWriteCompressed()) {

                writeParam.setCompressionMode(
                        javax.imageio.ImageWriteParam.MODE_EXPLICIT);

                writeParam.setCompressionType("Lossy");

                writeParam.setCompressionQuality(
                        webpQuality);
            }

            try (var imageOutput =
                    ImageIO.createImageOutputStream(output)) {

                writer.setOutput(imageOutput);

                writer.write(
                        null,
                        new javax.imageio.IIOImage(
                                image,
                                null,
                                null),
                        writeParam);
            }

            return output.toByteArray();

        } finally {

            writer.dispose();
        }
    }
}