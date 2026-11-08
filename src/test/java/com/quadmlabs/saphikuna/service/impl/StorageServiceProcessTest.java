package com.quadmlabs.saphikuna.service.impl;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Una foto de celular (12 megapíxeles) se procesa dentro del límite de tamaño, también cuando
 * varias llegan a la vez. En un servidor de 512 MB lo importante es que no se procesen en
 * paralelo: se puede comprobar con un heap chico, por ejemplo
 * {@code mvn test -Dtest=StorageServiceProcessTest -DargLine=-Xmx256m}.
 */
class StorageServiceProcessTest {

    private final StorageServiceImpl storage = new StorageServiceImpl(
            "http://localhost", "no-usada", "images", 1600, 1600, 15L * 1024 * 1024, 0.85f, 153_600, 400);

    /** JPEG de 4000x3000 con ruido (pesa varios MB, como una foto real). */
    private static byte[] phonePhoto(long seed) throws Exception {
        BufferedImage image = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int base = (x * 255 / image.getWidth()) << 16 | (y * 255 / image.getHeight()) << 8 | 128;
                image.setRGB(x, y, base ^ (random.nextInt(40) * 0x010101));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void aTwelveMegapixelPhotoFitsTheLimitAndGetsAThumbnail() throws Exception {
        byte[] raw = phonePhoto(1);

        StorageServiceImpl.Processed result = storage.process(raw);

        assertThat(raw.length).isGreaterThan(1_000_000);
        assertThat(result.main().length).isLessThanOrEqualTo(153_600);
        assertThat(result.thumb().length).isPositive().isLessThan(result.main().length);
    }

    @Test
    void severalPhotosAtOnceAreProcessedOneByOne() throws Exception {
        List<byte[]> photos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            photos.add(phonePhoto(i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<StorageServiceImpl.Processed>> futures = new ArrayList<>();
            for (byte[] photo : photos) {
                futures.add(pool.submit(() -> storage.process(photo)));
            }
            for (Future<StorageServiceImpl.Processed> future : futures) {
                assertThat(future.get().main().length).isLessThanOrEqualTo(153_600);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
