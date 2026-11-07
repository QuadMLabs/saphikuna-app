package com.quadmlabs.saphikuna.service.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Orientación EXIF de las fotos (la que guardan los celulares).
 *
 * Un celular guarda la foto "de lado" y anota en el EXIF cómo girarla; los
 * navegadores lo respetan, pero {@code ImageIO} no. Si no se aplica antes de
 * convertir a WebP (que descarta el EXIF), la foto queda girada en la tienda.
 *
 * Valores EXIF: 1 normal, 2 espejo horizontal, 3 giro 180°, 4 espejo vertical,
 * 5 transpuesta, 6 giro 90° horario, 7 transversa, 8 giro 90° antihorario.
 */
final class ImageOrientation {

    static final int NORMAL = 1;

    private static final int EXIF_ORIENTATION_TAG = 0x0112;

    private ImageOrientation() {
    }

    /** Lee la orientación de un JPEG; devuelve 1 si no hay EXIF o no es válido. */
    static int read(byte[] data) {
        try {
            return readOrThrow(data);
        } catch (RuntimeException e) {
            return NORMAL; // EXIF dañado o formato distinto: se deja la imagen como está
        }
    }

    private static int readOrThrow(byte[] d) {
        if (d.length < 4 || (d[0] & 0xFF) != 0xFF || (d[1] & 0xFF) != 0xD8) {
            return NORMAL; // no es JPEG
        }

        int i = 2;
        while (i + 4 <= d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marker = d[i + 1] & 0xFF;

            if (marker == 0xFF) {            // relleno
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;                      // marcadores sin longitud
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return NORMAL;               // empieza la imagen: ya no hay EXIF
            }

            int length = u16(d, i + 2, true);
            int start = i + 4;

            if (marker == 0xE1 && length >= 8 && isExifHeader(d, start)) {
                return readTiff(d, start + 6);
            }
            i += 2 + length;
        }
        return NORMAL;
    }

    private static boolean isExifHeader(byte[] d, int p) {
        return d[p] == 'E' && d[p + 1] == 'x' && d[p + 2] == 'i' && d[p + 3] == 'f'
                && d[p + 4] == 0 && d[p + 5] == 0;
    }

    private static int readTiff(byte[] d, int tiff) {
        boolean big = d[tiff] == 'M';
        int ifd = tiff + (int) u32(d, tiff + 4, big);
        int entries = u16(d, ifd, big);

        for (int k = 0; k < entries; k++) {
            int entry = ifd + 2 + 12 * k;
            if (u16(d, entry, big) == EXIF_ORIENTATION_TAG) {
                int value = u16(d, entry + 8, big);
                return value >= 1 && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }

    private static int u16(byte[] d, int p, boolean big) {
        int a = d[p] & 0xFF;
        int b = d[p + 1] & 0xFF;
        return big ? (a << 8) | b : (b << 8) | a;
    }

    private static long u32(byte[] d, int p, boolean big) {
        long hi = u16(d, big ? p : p + 2, big);
        long lo = u16(d, big ? p + 2 : p, big);
        return (hi << 16) | lo;
    }

    /** Devuelve la imagen ya girada/espejada (siempre RGB, fondo blanco). */
    static BufferedImage apply(BufferedImage src, int orientation) {

        int w = src.getWidth();
        int h = src.getHeight();

        AffineTransform t;
        switch (orientation) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> t = new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> t = new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> t = new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> t = new AffineTransform(0, -1, 1, 0, 0, w);
            default -> {
                return src;
            }
        }

        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, t, null);
        g.dispose();

        return out;
    }
}
