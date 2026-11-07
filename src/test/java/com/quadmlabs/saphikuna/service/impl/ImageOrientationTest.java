package com.quadmlabs.saphikuna.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rotación EXIF de fotos de celular. */
class ImageOrientationTest {

    private static final int W = 40;
    private static final int H = 20;
    private static final int RED = Color.RED.getRGB();
    private static final int BLUE = Color.BLUE.getRGB();

    /** Imagen 40x20: esquina superior izquierda roja, superior derecha azul, resto gris. */
    private static BufferedImage source() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, W, H);
        g.dispose();
        img.setRGB(0, 0, RED);
        img.setRGB(W - 1, 0, BLUE);
        return img;
    }

    /** JPEG con un segmento EXIF que declara la orientación indicada. */
    static byte[] jpegWithOrientation(BufferedImage img, int orientation, boolean bigEndian) throws Exception {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", plain);
        byte[] jpg = plain.toByteArray();

        ByteBuffer tiff = ByteBuffer.allocate(26).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) (bigEndian ? 'M' : 'I')).put((byte) (bigEndian ? 'M' : 'I'));
        tiff.putShort((short) 42);          // marca TIFF
        tiff.putInt(8);                      // offset del primer IFD
        tiff.putShort((short) 1);            // una entrada
        tiff.putShort((short) 0x0112);       // tag Orientation
        tiff.putShort((short) 3);            // tipo SHORT
        tiff.putInt(1);                      // cantidad
        tiff.putShort((short) orientation);  // valor
        tiff.putShort((short) 0);            // relleno
        tiff.putInt(0);                      // sin más IFD

        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
        exif.write(tiff.array());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpg, 0, 2);                                   // SOI
        out.write(0xFF);
        out.write(0xE1);                                        // APP1
        out.write((exif.size() + 2) >> 8);
        out.write((exif.size() + 2) & 0xFF);
        out.write(exif.toByteArray());
        out.write(jpg, 2, jpg.length - 2);
        return out.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 4, 5, 6, 7, 8 })
    void readsEveryOrientationInBothByteOrders(int orientation) throws Exception {
        assertEquals(orientation, ImageOrientation.read(jpegWithOrientation(source(), orientation, true)));
        assertEquals(orientation, ImageOrientation.read(jpegWithOrientation(source(), orientation, false)));
    }

    @Test
    void jpegWithoutExifOrInvalidDataIsNormal() throws Exception {
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        ImageIO.write(source(), "jpg", plain);

        assertEquals(1, ImageOrientation.read(plain.toByteArray()));
        assertEquals(1, ImageOrientation.read(new byte[0]));
        assertEquals(1, ImageOrientation.read(new byte[] { 1, 2, 3, 4, 5 }));

        // EXIF cortado a la mitad: no debe lanzar excepción
        byte[] withExif = jpegWithOrientation(source(), 6, true);
        assertEquals(1, ImageOrientation.read(java.util.Arrays.copyOf(withExif, 14)));

        // valor fuera de rango
        assertEquals(1, ImageOrientation.read(jpegWithOrientation(source(), 99, true)));
    }

    @Test
    void appliesEachOrientation() {
        // Para cada valor EXIF: dónde queda la esquina roja (origen) y la azul (arriba-derecha)
        // y qué dimensiones tiene el resultado.
        int[][] expected = {
                // orient, ancho, alto, rojoX, rojoY, azulX, azulY
                { 1, W, H, 0, 0, W - 1, 0 },
                { 2, W, H, W - 1, 0, 0, 0 },
                { 3, W, H, W - 1, H - 1, 0, H - 1 },
                { 4, W, H, 0, H - 1, W - 1, H - 1 },
                { 5, H, W, 0, 0, 0, W - 1 },
                { 6, H, W, H - 1, 0, H - 1, W - 1 },
                { 7, H, W, H - 1, W - 1, H - 1, 0 },
                { 8, H, W, 0, W - 1, 0, 0 },
        };

        for (int[] e : expected) {
            BufferedImage out = ImageOrientation.apply(source(), e[0]);
            String label = "orientación " + e[0];

            assertEquals(e[1], out.getWidth(), label + " ancho");
            assertEquals(e[2], out.getHeight(), label + " alto");
            assertEquals(RED, out.getRGB(e[3], e[4]) | 0xFF000000, label + " esquina roja");
            assertEquals(BLUE, out.getRGB(e[5], e[6]) | 0xFF000000, label + " esquina azul");
        }
    }

    @Test
    void phonePhotoComesOutUprightThroughTheWholePipeline() throws Exception {
        // Foto "vertical" tomada con celular: el sensor guarda 80x40 (horizontal) + EXIF 6
        BufferedImage sensor = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        byte[] raw = jpegWithOrientation(sensor, 6, true);

        StorageServiceImpl storage = new StorageServiceImpl(
                "http://localhost", "key", "images", 1200, 1200, 15L * 1024 * 1024, 0.82f, 150 * 1024, 400);

        StorageServiceImpl.Processed processed = storage.process(raw);

        BufferedImage main = ImageIO.read(new ByteArrayInputStream(processed.main()));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(processed.thumb()));

        assertTrue(main.getHeight() > main.getWidth(), "la imagen principal debe quedar vertical");
        assertTrue(thumb.getHeight() > thumb.getWidth(), "la miniatura debe quedar vertical");
        assertEquals(40, main.getWidth());
        assertEquals(80, main.getHeight());
    }
}
