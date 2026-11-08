package com.quadmlabs.saphikuna.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Detecta y quita la marca de agua "Dola AI" de las fotos de modelo generadas con IA.
 *
 * La marca siempre queda abajo a la derecha, con el mismo tamaño y sobre un fondo de estudio liso.
 * Se detecta comparando el "relieve" de esa zona con una plantilla (image/dola-ai-template.png,
 * aprendida de fotos reales) y se rellena con el fondo que la rodea:
 * <ol>
 * <li>Si el fondo es liso, se ajusta una superficie suave a su alrededor y se cubre el texto con ella
 * (con un poco de grano para que no se note el parche).</li>
 * <li>Si el texto cae sobre el borde de una prenda u otro objeto, la superficie se ajusta ignorando
 * esos píxeles atípicos y solo se cubren las letras y su sombra.</li>
 * <li>Si tampoco así hay un fondo fiable, se rellenan las letras por difusión desde los vecinos.</li>
 * </ol>
 * Si no hay marca, la imagen no se toca.
 */
public final class WatermarkRemover {

    public enum Method {
        /** No se encontró la marca. */
        NONE,
        /** Se cubrió con una superficie ajustada al fondo liso. */
        SURFACE,
        /** El texto cae sobre el borde de una prenda: se cubrieron solo las letras y su sombra. */
        LETTERS,
        /** Último recurso: letras rellenadas por difusión desde los píxeles vecinos. */
        DIFFUSION,
        /** Parece haber marca pero la detección es débil y el fondo no es liso: no se toca. */
        SKIPPED
    }

    public record Result(double score, Method method, double roughness) {
        public boolean detected() {
            return method != Method.NONE;
        }

        public boolean removed() {
            return method == Method.SURFACE || method == Method.LETTERS || method == Method.DIFFUSION;
        }
    }

    // Zona donde se busca (proporciones de la foto) y tamaño fijo con el que se compara
    private static final double X0 = 0.62, X1 = 1.0, Y0 = 0.86, Y1 = 1.0;
    private static final int RW = 380, RH = 140;

    /** Puntaje desde el que la detección es segura / desde el que se considera marca. */
    private static final double STRONG = 0.40, WEAK = 0.20;
    /** Desviación máxima del fondo para cubrirlo con una superficie lisa. */
    private static final double MAX_ROUGHNESS = 4.0;
    /** Para cubrir solo las letras (con el borde de una prenda cerca) se exige igual un fondo fiable. */
    private static final double MAX_ROUGHNESS_LETTERS = 4.0;

    private static final float[] TEMPLATE = new float[RW * RH]; // media cero
    private static final double TEMPLATE_NORM;
    private static final boolean[] MASK = new boolean[RW * RH];  // trazos del texto
    private static final boolean[] SOFT = new boolean[RW * RH];  // trazos y su borde tenue (para el relleno por difusión)
    private static final double BX0, BX1, BY0, BY1;              // caja del texto dentro de la zona (0..1)

    static {
        try (InputStream in = WatermarkRemover.class.getResourceAsStream("/image/dola-ai-template.png")) {
            if (in == null) {
                throw new IllegalStateException("Falta image/dola-ai-template.png");
            }
            BufferedImage png = ImageIO.read(in);
            double sum = 0;
            for (int y = 0; y < RH; y++) {
                for (int x = 0; x < RW; x++) {
                    // getSample: el valor tal cual está en el archivo (getRGB lo pasaría por la conversión de color)
                    float v = png.getRaster().getSample(x, y, 0) / 255f;
                    TEMPLATE[y * RW + x] = v;
                    MASK[y * RW + x] = v > 0.22f;
                    SOFT[y * RW + x] = v > 0.08f;
                    sum += v;
                }
            }
            double mean = sum / (RW * RH);
            double norm = 0;
            for (int i = 0; i < TEMPLATE.length; i++) {
                TEMPLATE[i] -= (float) mean;
                norm += TEMPLATE[i] * (double) TEMPLATE[i];
            }
            TEMPLATE_NORM = Math.sqrt(norm);

            int minX = RW, maxX = 0, minY = RH, maxY = 0;
            for (int y = 0; y < RH; y++) {
                for (int x = 0; x < RW; x++) {
                    if (MASK[y * RW + x]) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            BX0 = minX / (double) RW;
            BX1 = maxX / (double) RW;
            BY0 = minY / (double) RH;
            BY1 = maxY / (double) RH;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private WatermarkRemover() {
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /** Solo mide: no modifica la imagen. */
    public static Result analyze(BufferedImage image) {
        return process(image, false);
    }

    /** Si la foto tiene la marca, la quita (modifica la imagen recibida). */
    public static Result removeIfPresent(BufferedImage image) {
        return process(image, true);
    }

    // ------------------------------------------------------------------
    // Detección
    // ------------------------------------------------------------------

    /** Dónde se encontró la marca: la zona de búsqueda ya desplazada/escalada. */
    private record Placement(double score, double cx, double cy, double halfX, double halfY) {
    }

    private static Result process(BufferedImage image, boolean modify) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 300 || h < 300) {
            return new Result(0, Method.NONE, 0);
        }

        Placement p = detect(image);
        if (p.score() < WEAK) {
            return new Result(p.score(), Method.NONE, 0);
        }

        double left = p.cx() - p.halfX();
        double top = p.cy() - p.halfY();
        double boxX0 = left + BX0 * 2 * p.halfX();
        double boxX1 = left + BX1 * 2 * p.halfX();
        double boxY0 = top + BY0 * 2 * p.halfY();
        double boxY1 = top + BY1 * 2 * p.halfY();

        Fill fill = surfaceFill(image, boxX0, boxX1, boxY0, boxY1);
        boolean usable = fill.coef != null && fill.roughness <= MAX_ROUGHNESS;

        // 1) Fondo liso alrededor: se cubre toda la caja del texto
        if (usable && fill.outlierFraction <= 0.02) {
            if (modify) {
                fill.apply(image);
            }
            return new Result(p.score(), Method.SURFACE, fill.roughness);
        }
        // 2) Hay algo más en el borde (prenda, objeto) pero el fondo se ajusta bien sin ello:
        //    se cubren solo las letras y su sombra
        if (fill.coef != null && fill.roughness <= MAX_ROUGHNESS_LETTERS && p.score() >= STRONG) {
            if (modify) {
                fill.applyLetters(image, buildMask(image, p, left, top));
            }
            return new Result(p.score(), Method.LETTERS, fill.roughness);
        }
        // 3) Sin un fondo fiable: si la detección es débil no se toca; si es segura, difusión
        if (p.score() < STRONG) {
            return new Result(p.score(), Method.SKIPPED, fill.roughness);
        }
        if (modify) {
            diffuse(image, buildMask(image, p, left, top));
        }
        return new Result(p.score(), Method.DIFFUSION, fill.roughness);
    }

    private static Placement detect(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        // Se lee una sola vez la esquina inferior derecha y se arma una tabla de sumas para poder
        // reducir cualquier zona al tamaño fijo con rapidez.
        int ax0 = (int) (w * 0.5);
        int ay0 = (int) (h * 0.78);
        int aw = w - ax0;
        int ah = h - ay0;
        int[] rgb = image.getRGB(ax0, ay0, aw, ah, null, 0, aw);
        double[] sat = new double[(aw + 1) * (ah + 1)];
        for (int y = 0; y < ah; y++) {
            double row = 0;
            for (int x = 0; x < aw; x++) {
                int c = rgb[y * aw + x];
                double g = 0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF);
                row += g;
                sat[(y + 1) * (aw + 1) + (x + 1)] = sat[y * (aw + 1) + (x + 1)] + row;
            }
        }
        Area area = new Area(sat, aw, ah, ax0, ay0);

        double cx0 = (X0 + X1) / 2;
        double cy0 = (Y0 + Y1) / 2;
        double hx0 = (X1 - X0) / 2;
        double hy0 = (Y1 - Y0) / 2;

        Placement best = new Placement(correlation(area, w, h, cx0, cy0, hx0, hy0), cx0, cy0, hx0, hy0);
        if (best.score() >= STRONG) {
            return best;
        }
        for (double sx : new double[] { 0.85, 1.0, 1.15 }) {
            for (double dx : new double[] { -0.02, 0, 0.02 }) {
                for (double dy : new double[] { -0.01, 0, 0.01 }) {
                    double cx = cx0 + dx;
                    double cy = cy0 + dy;
                    double score = correlation(area, w, h, cx, cy, hx0 * sx, hy0 * sx);
                    if (score > best.score()) {
                        best = new Placement(score, cx, cy, hx0 * sx, hy0 * sx);
                    }
                }
            }
        }
        return best;
    }

    private record Area(double[] sat, int w, int h, int x0, int y0) {
        /** Promedio de gris del bloque [x0,x1)x[y0,y1) en coordenadas de la foto (los bordes se repiten). */
        double mean(double bx0, double bx1, double by0, double by1) {
            int xa = clamp((int) Math.floor(bx0) - x0, 0, w - 1);
            int xb = clamp((int) Math.ceil(bx1) - x0, xa + 1, w);
            int ya = clamp((int) Math.floor(by0) - y0, 0, h - 1);
            int yb = clamp((int) Math.ceil(by1) - y0, ya + 1, h);
            int stride = w + 1;
            double total = sat[yb * stride + xb] - sat[ya * stride + xb] - sat[yb * stride + xa] + sat[ya * stride + xa];
            return total / ((double) (xb - xa) * (yb - ya));
        }
    }

    /** Correlación entre el relieve de la zona y la plantilla (1 = idéntico, 0 = nada que ver). */
    private static double correlation(Area area, int w, int h, double cx, double cy, double hx, double hy) {
        double left = (cx - hx) * w;
        double top = (cy - hy) * h;
        double stepX = 2 * hx * w / RW;
        double stepY = 2 * hy * h / RH;

        float[] g = new float[RW * RH];
        for (int j = 0; j < RH; j++) {
            for (int i = 0; i < RW; i++) {
                g[j * RW + i] = (float) area.mean(left + i * stepX, left + (i + 1) * stepX, top + j * stepY, top + (j + 1) * stepY);
            }
        }
        float[] blurred = gaussian(g, RW, RH, 7);
        double mean = 0;
        for (int i = 0; i < g.length; i++) {
            g[i] -= blurred[i];
            mean += g[i];
        }
        mean /= g.length;
        double dot = 0;
        double norm = 0;
        for (int i = 0; i < g.length; i++) {
            double a = g[i] - mean;
            dot += a * TEMPLATE[i];
            norm += a * a;
        }
        return dot / (Math.sqrt(norm) * TEMPLATE_NORM + 1e-6);
    }

    private static float[] gaussian(float[] src, int w, int h, double sigma) {
        int r = (int) Math.ceil(sigma * 3);
        float[] k = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) {
            k[i + r] = (float) Math.exp(-(i * (double) i) / (2 * sigma * sigma));
            sum += k[i + r];
        }
        for (int i = 0; i < k.length; i++) {
            k[i] /= sum;
        }
        float[] tmp = new float[w * h];
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0;
                for (int i = -r; i <= r; i++) {
                    s += k[i + r] * src[y * w + clamp(x + i, 0, w - 1)];
                }
                tmp[y * w + x] = s;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0;
                for (int i = -r; i <= r; i++) {
                    s += k[i + r] * tmp[clamp(y + i, 0, h - 1) * w + x];
                }
                out[y * w + x] = s;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Relleno 1: superficie ajustada al fondo que rodea el texto
    // ------------------------------------------------------------------

    private static final class Fill {
        int x0, y0, x1, y1;
        double sx, sy;
        double[][] coef;          // 3 canales x 6 coeficientes (null si no hubo suficiente fondo)
        double sigma;             // grano
        double roughness;         // cuánto se aparta el fondo "bueno" de la superficie ajustada
        double outlierFraction;   // parte del fondo que se descartó por no ser fondo (prenda, objetos)

        double surface(int c, int x, int y) {
            double u = (x - x0) / sx;
            double v = (y - y0) / sy;
            return coef[c][0] + coef[c][1] * u + coef[c][2] * v + coef[c][3] * u * v + coef[c][4] * u * u + coef[c][5] * v * v;
        }

        /** Cubre toda la caja del texto con la superficie (más un poco de grano). */
        void apply(BufferedImage image) {
            Random random = new Random(7);
            int feather = 5;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int old = image.getRGB(x, y);
                    double edge = Math.min(Math.min(x - x0, x1 - x), Math.min(y - y0, y1 - y));
                    double alpha = Math.min(1.0, (edge + 1) / feather);
                    image.setRGB(x, y, blend(old, x, y, alpha, random));
                }
            }
        }

        /** Cubre solo las letras (y su sombra) con la superficie; el resto de la foto no se toca. */
        void applyLetters(BufferedImage image, Mask mask) {
            Random random = new Random(7);
            float[] alpha = mask.softAlpha();
            for (int y = 0; y < mask.bh; y++) {
                for (int x = 0; x < mask.bw; x++) {
                    float a = alpha[y * mask.bw + x];
                    if (a > 0.01f) {
                        int px = mask.bx0 + x;
                        int py = mask.by0 + y;
                        image.setRGB(px, py, blend(image.getRGB(px, py), px, py, a, random));
                    }
                }
            }
        }

        private int blend(int old, int x, int y, double alpha, Random random) {
            int rgb = old & 0xFF000000;
            for (int c = 0; c < 3; c++) {
                double filled = surface(c, x, y) + random.nextGaussian() * sigma;
                int shift = 16 - 8 * c;
                double original = (old >> shift) & 0xFF;
                int value = clamp((int) Math.round(filled * alpha + original * (1 - alpha)), 0, 255);
                rgb |= value << shift;
            }
            return rgb;
        }
    }

    /**
     * Ajusta una superficie suave (un polinomio de segundo grado por canal) al fondo que rodea la caja
     * del texto. El ajuste es robusto: los píxeles que no se parecen al fondo (el borde de una prenda,
     * un objeto) se descartan y se vuelve a ajustar.
     */
    private static Fill surfaceFill(BufferedImage image, double fx0, double fx1, double fy0, double fy1) {
        int w = image.getWidth();
        int h = image.getHeight();
        int padX = (int) (w * 0.018);
        int padY = (int) (h * 0.012);
        Fill fill = new Fill();
        fill.x0 = Math.max((int) (w * fx0) - padX, 0);
        fill.x1 = Math.min((int) (w * fx1) + padX, w - 1);
        fill.y0 = Math.max((int) (h * fy0) - padY, 0);
        fill.y1 = Math.min((int) (h * fy1) + padY, h - 1);
        fill.sx = Math.max(fill.x1 - fill.x0, 1);
        fill.sy = Math.max(fill.y1 - fill.y0, 1);

        int ring = 14;
        int rx0 = Math.max(fill.x0 - ring, 0);
        int ry0 = Math.max(fill.y0 - ring, 0);
        int rx1 = Math.min(fill.x1 + ring, w - 1);
        int ry1 = Math.min(fill.y1 + ring, h - 1);
        int rw = rx1 - rx0 + 1;
        int[] pixels = image.getRGB(rx0, ry0, rw, ry1 - ry0 + 1, null, 0, rw);

        // Píxeles del anillo exterior
        int cap = pixels.length;
        int[] xs = new int[cap];
        int[] ys = new int[cap];
        int[] col = new int[cap];
        int n = 0;
        for (int y = ry0; y <= ry1; y++) {
            for (int x = rx0; x <= rx1; x++) {
                if (x >= fill.x0 && x <= fill.x1 && y >= fill.y0 && y <= fill.y1) {
                    continue;
                }
                xs[n] = x;
                ys[n] = y;
                col[n] = pixels[(y - ry0) * rw + (x - rx0)];
                n++;
            }
        }

        boolean[] keep = new boolean[n];
        java.util.Arrays.fill(keep, true);
        double[][] coef = null;
        for (int pass = 0; pass < 4; pass++) {
            coef = fitSurface(xs, ys, col, keep, n, fill);
            if (coef == null) {
                fill.roughness = Double.MAX_VALUE;
                return fill;
            }
            if (pass == 3) {
                break;
            }
            fill.coef = coef;
            double[] resid = new double[n];
            int kept = 0;
            for (int i = 0; i < n; i++) {
                resid[i] = maxResidual(fill, xs[i], ys[i], col[i]);
                if (keep[i]) {
                    kept++;
                }
            }
            double[] sample = new double[kept];
            int j = 0;
            for (int i = 0; i < n; i++) {
                if (keep[i]) {
                    sample[j++] = resid[i];
                }
            }
            java.util.Arrays.sort(sample);
            double sigma = 1.4826 * sample[sample.length / 2];
            // Primera vuelta: como el primer ajuste puede estar torcido por la prenda, se conserva solo el
            // 65 % que mejor encaja; después se descarta lo que se aparte más de 3 desviaciones.
            double limit = pass == 0 ? sample[(int) (sample.length * 0.65)] : Math.max(3 * sigma, 4.0);
            for (int i = 0; i < n; i++) {
                keep[i] = resid[i] <= limit;
            }
        }
        fill.coef = coef;

        // Rugosidad y parte descartada, con el fondo que se conservó
        int kept = 0;
        for (boolean k : keep) {
            if (k) {
                kept++;
            }
        }
        double worst = 0;
        for (int c = 0; c < 3; c++) {
            double sum = 0;
            double sumSq = 0;
            for (int i = 0; i < n; i++) {
                if (!keep[i]) {
                    continue;
                }
                double r = ((col[i] >> (16 - 8 * c)) & 0xFF) - fill.surface(c, xs[i], ys[i]);
                sum += r;
                sumSq += r * r;
            }
            double mean = sum / kept;
            worst = Math.max(worst, Math.sqrt(Math.max(sumSq / kept - mean * mean, 0)));
        }
        fill.roughness = worst;
        // grano parecido al del fondo, pero nunca fuerte (si el fondo es rugoso, el grano no debe notarse)
        fill.sigma = Math.min(Math.max(worst * 0.8, 0.4), 1.6);
        fill.outlierFraction = 1.0 - kept / (double) n;
        return fill;
    }

    private static double maxResidual(Fill fill, int x, int y, int rgb) {
        double worst = 0;
        for (int c = 0; c < 3; c++) {
            worst = Math.max(worst, Math.abs(((rgb >> (16 - 8 * c)) & 0xFF) - fill.surface(c, x, y)));
        }
        return worst;
    }

    /** Mínimos cuadrados sobre los píxeles que se conservan; null si quedan muy pocos. */
    private static double[][] fitSurface(int[] xs, int[] ys, int[] col, boolean[] keep, int n, Fill fill) {
        double[][] ata = new double[6][6];
        double[][] atb = new double[6][3];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (!keep[i]) {
                continue;
            }
            double u = (xs[i] - fill.x0) / fill.sx;
            double v = (ys[i] - fill.y0) / fill.sy;
            double[] b = { 1, u, v, u * v, u * u, v * v };
            double[] val = { (col[i] >> 16) & 0xFF, (col[i] >> 8) & 0xFF, col[i] & 0xFF };
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 6; c++) {
                    ata[r][c] += b[r] * b[c];
                }
                for (int ch = 0; ch < 3; ch++) {
                    atb[r][ch] += b[r] * val[ch];
                }
            }
            count++;
        }
        if (count < 50) {
            return null;
        }
        double[][] solved = solve(ata, atb);
        double[][] coef = new double[3][6];
        for (int ch = 0; ch < 3; ch++) {
            for (int k = 0; k < 6; k++) {
                coef[ch][k] = solved[k][ch];
            }
        }
        return coef;
    }

    /** Resuelve A x = B por eliminación gaussiana (A de 6x6, B de 6xN). */
    private static double[][] solve(double[][] a, double[][] b) {
        int n = a.length;
        int m = b[0].length;
        double[][] x = new double[n][n + m];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, x[i], 0, n);
            System.arraycopy(b[i], 0, x[i], n, m);
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(x[r][col]) > Math.abs(x[pivot][col])) {
                    pivot = r;
                }
            }
            double[] tmp = x[col];
            x[col] = x[pivot];
            x[pivot] = tmp;
            double d = Math.abs(x[col][col]) < 1e-12 ? 1e-12 : x[col][col];
            for (int j = col; j < n + m; j++) {
                x[col][j] /= d;
            }
            for (int r = 0; r < n; r++) {
                if (r != col) {
                    double f = x[r][col];
                    for (int j = col; j < n + m; j++) {
                        x[r][j] -= f * x[col][j];
                    }
                }
            }
        }
        double[][] out = new double[n][m];
        for (int i = 0; i < n; i++) {
            System.arraycopy(x[i], n, out[i], 0, m);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Máscara de las letras (y su sombra) puesta sobre la foto
    // ------------------------------------------------------------------

    private record Mask(boolean[] grown, int bx0, int by0, int bw, int bh) {
        /** Bordes suaves: 1 dentro de la máscara y baja a 0 en unos 4 píxeles hacia afuera. */
        float[] softAlpha() {
            float[] a = new float[bw * bh];
            for (int i = 0; i < a.length; i++) {
                a[i] = grown[i] ? 1f : 0f;
            }
            for (int pass = 0; pass < 2; pass++) {
                float[] b = new float[a.length];
                for (int y = 0; y < bh; y++) {
                    for (int x = 0; x < bw; x++) {
                        float s = 0;
                        int c = 0;
                        for (int dy = -2; dy <= 2; dy++) {
                            for (int dx = -2; dx <= 2; dx++) {
                                int xx = x + dx;
                                int yy = y + dy;
                                if (xx >= 0 && xx < bw && yy >= 0 && yy < bh) {
                                    s += a[yy * bw + xx];
                                    c++;
                                }
                            }
                        }
                        b[y * bw + x] = s / c;
                    }
                }
                a = b;
            }
            // Dentro de la máscara siempre se cubre del todo
            for (int i = 0; i < a.length; i++) {
                if (grown[i]) {
                    a[i] = 1f;
                }
            }
            return a;
        }
    }

    /** Trazos de la plantilla colocados sobre la foto y engrosados para cubrir también la sombra. */
    private static Mask buildMask(BufferedImage image, Placement p, double left, double top) {
        int w = image.getWidth();
        int h = image.getHeight();
        int margin = 22;
        int bx0 = clamp((int) (w * (left + BX0 * 2 * p.halfX())) - margin, 1, w - 2);
        int bx1 = clamp((int) (w * (left + BX1 * 2 * p.halfX())) + margin, 1, w - 2);
        int by0 = clamp((int) (h * (top + BY0 * 2 * p.halfY())) - margin, 1, h - 2);
        int by1 = clamp((int) (h * (top + BY1 * 2 * p.halfY())) + margin, 1, h - 2);
        int bw = bx1 - bx0 + 1;
        int bh = by1 - by0 + 1;

        boolean[] mask = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                double u = ((bx0 + x) / (double) w - left) / (2 * p.halfX());
                double v = ((by0 + y) / (double) h - top) / (2 * p.halfY());
                int tx = (int) (u * RW);
                int ty = (int) (v * RH);
                mask[y * bw + x] = tx >= 0 && tx < RW && ty >= 0 && ty < RH && SOFT[ty * RW + tx];
            }
        }
        // la sombra y el brillo de las letras se extienden bastante más allá del trazo
        int radius = Math.max(10, (int) (w * 0.011));
        boolean[] grown = new boolean[bw * bh];
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                if (!mask[y * bw + x]) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int yy = y + dy;
                        int xx = x + dx;
                        if (xx >= 0 && xx < bw && yy >= 0 && yy < bh && dx * dx + dy * dy <= radius * radius) {
                            grown[yy * bw + xx] = true;
                        }
                    }
                }
            }
        }
        return new Mask(grown, bx0, by0, bw, bh);
    }

    // ------------------------------------------------------------------
    // Último recurso: rellenar las letras por difusión desde los vecinos
    // ------------------------------------------------------------------

    private static void diffuse(BufferedImage image, Mask m) {
        int bw = m.bw();
        int bh = m.bh();
        boolean[] grown = m.grown();

        float[][] ch = new float[3][bw * bh];
        int[] rgb = image.getRGB(m.bx0(), m.by0(), bw, bh, null, 0, bw);
        for (int i = 0; i < rgb.length; i++) {
            ch[0][i] = (rgb[i] >> 16) & 0xFF;
            ch[1][i] = (rgb[i] >> 8) & 0xFF;
            ch[2][i] = rgb[i] & 0xFF;
        }
        for (int c = 0; c < 3; c++) {
            float[] cur = ch[c];
            float[] next = cur.clone();
            for (int iter = 0; iter < 400; iter++) {
                for (int y = 1; y < bh - 1; y++) {
                    for (int x = 1; x < bw - 1; x++) {
                        int i = y * bw + x;
                        if (grown[i]) {
                            next[i] = (cur[i - 1] + cur[i + 1] + cur[i - bw] + cur[i + bw]) * 0.25f;
                        }
                    }
                }
                float[] tmp = cur;
                cur = next;
                next = tmp;
            }
            ch[c] = cur;
        }
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int i = y * bw + x;
                if (grown[i]) {
                    int px = m.bx0() + x;
                    int py = m.by0() + y;
                    int pixel = (image.getRGB(px, py) & 0xFF000000)
                            | (clamp(Math.round(ch[0][i]), 0, 255) << 16)
                            | (clamp(Math.round(ch[1][i]), 0, 255) << 8)
                            | clamp(Math.round(ch[2][i]), 0, 255);
                    image.setRGB(px, py, pixel);
                }
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
