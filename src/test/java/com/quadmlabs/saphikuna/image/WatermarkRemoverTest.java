package com.quadmlabs.saphikuna.image;

import com.quadmlabs.saphikuna.image.WatermarkRemover.Method;
import com.quadmlabs.saphikuna.image.WatermarkRemover.Result;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La marca "Dola AI" se detecta y se quita sin tocar el resto de la foto. Las pruebas usan fotos
 * sintéticas: un fondo de estudio liso al que se le pinta la marca con la misma plantilla.
 */
class WatermarkRemoverTest {

    private static final int W = 1200;
    private static final int H = 1600;

    /** Fondo de estudio: degradado suave con un poco de grano. */
    private static BufferedImage studio(long seed) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int base = 200 + (int) (25.0 * x / W) - (int) (20.0 * y / H);
                int noise = random.nextInt(3) - 1;
                int c = Math.max(0, Math.min(255, base + noise));
                image.setRGB(x, y, c << 16 | (c + 4 > 255 ? 255 : c + 4) << 8 | c);
            }
        }
        return image;
    }

    /** Pinta la marca (texto blanco) en su lugar de siempre: abajo a la derecha. */
    private static void stamp(BufferedImage image) throws Exception {
        BufferedImage template;
        try (InputStream in = WatermarkRemoverTest.class.getResourceAsStream("/image/dola-ai-template.png")) {
            template = ImageIO.read(in);
        }
        int x0 = (int) (W * 0.62);
        int y0 = (int) (H * 0.86);
        int rw = W - x0;
        int rh = H - y0;
        for (int y = y0; y < H; y++) {
            for (int x = x0; x < W; x++) {
                int tx = Math.min(379, (x - x0) * 380 / rw);
                int ty = Math.min(139, (y - y0) * 140 / rh);
                double v = template.getRaster().getSample(tx, ty, 0) / 255.0;
                if (v > 0.15) {
                    double a = Math.min(1.0, v * 1.4);
                    int old = image.getRGB(x, y);
                    int r = (int) (((old >> 16) & 0xFF) * (1 - a) + 255 * a);
                    int g = (int) (((old >> 8) & 0xFF) * (1 - a) + 255 * a);
                    int b = (int) ((old & 0xFF) * (1 - a) + 255 * a);
                    image.setRGB(x, y, r << 16 | g << 8 | b);
                }
            }
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /** Diferencia media (por canal) entre dos fotos dentro de la zona de la marca. */
    private static double meanDifference(BufferedImage a, BufferedImage b) {
        long sum = 0;
        int count = 0;
        for (int y = (int) (H * 0.93); y < (int) (H * 0.97); y++) {
            for (int x = (int) (W * 0.80); x < (int) (W * 0.97); x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                sum += Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                        + Math.abs((p & 0xFF) - (q & 0xFF));
                count += 3;
            }
        }
        return sum / (double) count;
    }

    @Test
    void detectsTheMarkOnAStudioBackground() throws Exception {
        BufferedImage photo = studio(1);
        stamp(photo);

        Result result = WatermarkRemover.analyze(photo);

        assertThat(result.detected()).isTrue();
        assertThat(result.score()).isGreaterThan(0.4);
        assertThat(result.method()).isEqualTo(Method.SURFACE);
    }

    @Test
    void removingItLeavesTheBackgroundAsItWas() throws Exception {
        BufferedImage clean = studio(2);
        BufferedImage marked = studio(2);
        stamp(marked);
        double before = meanDifference(clean, marked);

        Result result = WatermarkRemover.removeIfPresent(marked);

        assertThat(result.removed()).isTrue();
        assertThat(before).isGreaterThan(3.0);                      // la marca se notaba
        assertThat(meanDifference(clean, marked)).isLessThan(before / 4);  // ya casi no se distingue del fondo
    }

    @Test
    void doesNotTouchAnythingOutsideTheMarkArea() throws Exception {
        BufferedImage photo = studio(3);
        stamp(photo);
        int[] before = pixels(photo);

        WatermarkRemover.removeIfPresent(photo);
        int[] after = pixels(photo);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean nearMark = x > W * 0.74 && y > H * 0.90; // la caja del texto más su margen
                if (!nearMark) {
                    assertThat(after[y * W + x]).as("píxel (%d,%d)", x, y).isEqualTo(before[y * W + x]);
                }
            }
        }
    }

    @Test
    void aPhotoWithoutTheMarkIsLeftExactlyAsItIs() {
        BufferedImage photo = studio(4);
        int[] before = pixels(photo);

        Result result = WatermarkRemover.removeIfPresent(photo);

        assertThat(result.method()).isEqualTo(Method.NONE);
        assertThat(pixels(photo)).isEqualTo(before);
    }

    @Test
    void aBusyPhotoWithoutTheMarkIsNotMistakenForOne() {
        // Mesa de madera: vetas fuertes y horizontales en toda la foto
        BufferedImage photo = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(5);
        for (int y = 0; y < H; y++) {
            int vein = 90 + (int) (30 * Math.sin(y / 9.0)) + random.nextInt(6);
            for (int x = 0; x < W; x++) {
                int r = Math.max(0, Math.min(255, vein + (x / 40) % 7));
                photo.setRGB(x, y, r << 16 | (r / 2) << 8 | (r / 3));
            }
        }
        int[] before = pixels(photo);

        Result result = WatermarkRemover.removeIfPresent(photo);

        assertThat(result.detected()).isFalse();
        assertThat(pixels(photo)).isEqualTo(before);
    }

    @Test
    void tinyImagesAreIgnored() {
        BufferedImage tiny = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);

        assertThat(WatermarkRemover.removeIfPresent(tiny).method()).isEqualTo(Method.NONE);
    }

    @Test
    void worksOnPhotosThatAreNotRgbTypeToo() throws Exception {
        // El decodificador de fotos puede devolver 3BYTE_BGR u otros tipos
        BufferedImage rgb = studio(6);
        stamp(rgb);
        BufferedImage bgr = new BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR);
        bgr.getGraphics().drawImage(rgb, 0, 0, null);

        Result result = WatermarkRemover.removeIfPresent(bgr);

        assertThat(result.removed()).isTrue();
    }
}
