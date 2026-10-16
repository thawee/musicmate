package apincer.music.core.authenticity;

import java.util.Arrays;

/**
 * Reads the upper spectrum of a track for the marks that resampling and lossy encoding leave.
 *
 * <ul>
 * <li><b>Cliff:</b> a resampler brick-walls everything above the source's Nyquist frequency, so a file
 * upsampled from 44.1 kHz drops 50 dB or more within a kilohertz of 22.05 kHz (24 kHz from 48 kHz).
 * Lossy encoders low-pass the same way, lower down (about 16 to 20 kHz).</li>
 * <li><b>Images:</b> a cheap resampler leaves no cliff but mirrors the audible spectrum above
 * 22.05 kHz, rising with frequency, which no recording does.</li>
 * </ul>
 *
 * <p>Natural recordings fall off gradually, often to almost nothing above 20 kHz, so the absence of
 * these marks is all this can report: never that a file is genuinely hi-res. High-bitrate lossy
 * encoding (AAC at 256 kbps) can keep everything up to 22 kHz and is not detectable here. Thresholds
 * were set on live recordings and fakes made from them (tasks/todo.md, Phase 3b).</p>
 */
public final class SpectralCliffDetector {

    public static final double BAND_HZ = 250.0;
    /** dB lost within about 1.5 kHz: far steeper than any natural rolloff. */
    static final double DROP_DB = 30.0;
    /** dB above the format's rounding floor for there to be anything to judge. */
    static final double SIGNAL_DB = 12.0;
    /** Lowest frequency searched for a cliff. */
    static final double SEARCH_FROM_HZ = 15000.0;

    public enum Kind {
        UPSAMPLED_FROM_44,
        UPSAMPLED_FROM_48,
        UPSAMPLED_FROM_44_IMAGES,
        UPSAMPLED_FROM_48_IMAGES,
        LOSSY,
        NO_SIGN_OF_UPSAMPLING,
        NO_LOSSY_CUTOFF,
        CANT_TELL
    }

    public static final class Finding {
        public final Kind kind;
        /** Where the spectrum falls off, in Hz, or NaN when there is no cliff. */
        public final double edgeHz;

        Finding(Kind kind, double edgeHz) {
            this.kind = kind;
            this.edgeHz = edgeHz;
        }
    }

    private SpectralCliffDetector() {
    }

    /**
     * @param bandsDb   power spectral density per {@link #BAND_HZ} band, from {@link SpectrumAccumulator}
     * @param bitDepth  the file's bit depth, which sets the rounding floor (16 when unknown)
     */
    public static Finding classify(double[] bandsDb, int sampleRate, int bitDepth) {
        double floor = quantizationFloorDb(sampleRate, bitDepth > 0 ? bitDepth : 16);
        double edge = findCliff(bandsDb, sampleRate, floor);
        boolean cliff = !Double.isNaN(edge);
        // Content where a cliff would show: just under 22.05 kHz in a hi-res file, 15 to 19 kHz at CD rates
        double signal = (sampleRate > 48000 ? level(bandsDb, 18000, 21500) : level(bandsDb, 15000, 19000)) - floor;

        if (cliff && edge < 21000) return new Finding(Kind.LOSSY, edge);
        if (sampleRate > 48000) {
            if (cliff && edge < 22600) return new Finding(Kind.UPSAMPLED_FROM_44, edge);
            if (cliff && edge < 24600) return new Finding(Kind.UPSAMPLED_FROM_48, edge);
            if (mirrored(bandsDb, 22050)) return new Finding(Kind.UPSAMPLED_FROM_44_IMAGES, Double.NaN);
            if (mirrored(bandsDb, 24000)) return new Finding(Kind.UPSAMPLED_FROM_48_IMAGES, Double.NaN);
            if (signal < SIGNAL_DB) return new Finding(Kind.CANT_TELL, Double.NaN);
            return new Finding(Kind.NO_SIGN_OF_UPSAMPLING, Double.NaN);
        }
        if (signal < SIGNAL_DB) return new Finding(Kind.CANT_TELL, Double.NaN);
        if (sampleRate == 48000 && cliff && edge < 22600) return new Finding(Kind.UPSAMPLED_FROM_44, edge);
        return new Finding(Kind.NO_LOSSY_CUTOFF, Double.NaN);
    }

    /** Spectral density of plain rounding noise at this bit depth, as {@link SpectrumAccumulator} measures it (within 3 dB). */
    static double quantizationFloorDb(int sampleRate, int bitDepth) {
        double q = Math.pow(2, -(bitDepth - 1));
        return 10 * Math.log10(q * q / 12 / (sampleRate / 2.0) / 2);
    }

    /**
     * The lowest edge, from 15 kHz up, where the level just below is DROP_DB above the level just above
     * and above everything beyond it; NaN when there is none.
     */
    static double findCliff(double[] db, int sampleRate, double floor) {
        double nyquist = sampleRate / 2.0;
        for (double e = SEARCH_FROM_HZ; e <= nyquist - 1500; e += BAND_HZ) {
            double below = level(db, e - 1500, e - 250);
            double above = level(db, e + 250, e + 1500);
            double rest = percentile(db, (int) ((e + 250) / BAND_HZ), 0.9);
            if (below - floor >= SIGNAL_DB && below - above >= DROP_DB && below - rest >= DROP_DB) {
                // The edge: the first band, from e - 1.5 kHz up, below the midpoint of the two levels
                double mid = (below + above) / 2;
                int i = (int) ((e - 1500) / BAND_HZ);
                while (i < db.length && db[i] >= mid) i++;
                return i * BAND_HZ;
            }
        }
        return Double.NaN;
    }

    /** Above the edge, the spectrum mirrors the one below it and rises with frequency. */
    static boolean mirrored(double[] db, double edge) {
        int n = 0;
        double[] up = new double[38];
        double[] down = new double[38];
        double[] d = new double[38];
        for (double x = 500; x < 10000; x += BAND_HZ) {
            up[n] = level(db, edge + x, edge + x + BAND_HZ);
            down[n] = level(db, edge - x - BAND_HZ, edge - x);
            if (Double.isNaN(up[n]) || Double.isNaN(down[n])) return false;
            d[n++] = x;
        }
        return correlation(up, down, n) > 0.8 && slope(d, up, n) > 0;
    }

    /** Median of the bands covering [lo, hi), NaN when none. */
    static double level(double[] db, double lo, double hi) {
        int a = Math.max((int) (lo / BAND_HZ), 0);
        int b = Math.min(Math.max((int) (hi / BAND_HZ), a + 1), db.length);
        if (a >= b) return Double.NaN;
        double[] seg = Arrays.copyOfRange(db, a, b);
        Arrays.sort(seg);
        int m = seg.length / 2;
        return seg.length % 2 == 1 ? seg[m] : (seg[m - 1] + seg[m]) / 2;
    }

    /** Linear-interpolated percentile of the bands from index {@code from} on, as numpy.percentile. */
    static double percentile(double[] db, int from, double p) {
        if (from >= db.length) return Double.NaN;
        double[] seg = Arrays.copyOfRange(db, from, db.length);
        Arrays.sort(seg);
        double pos = p * (seg.length - 1);
        int i = (int) Math.floor(pos);
        return i + 1 < seg.length ? seg[i] + (pos - i) * (seg[i + 1] - seg[i]) : seg[i];
    }

    private static double correlation(double[] a, double[] b, int n) {
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += a[i]; mb += b[i]; }
        ma /= n; mb /= n;
        double ab = 0, aa = 0, bb = 0;
        for (int i = 0; i < n; i++) {
            ab += (a[i] - ma) * (b[i] - mb);
            aa += (a[i] - ma) * (a[i] - ma);
            bb += (b[i] - mb) * (b[i] - mb);
        }
        return aa > 0 && bb > 0 ? ab / Math.sqrt(aa * bb) : 0;
    }

    private static double slope(double[] x, double[] y, int n) {
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double xy = 0, xx = 0;
        for (int i = 0; i < n; i++) {
            xy += (x[i] - mx) * (y[i] - my);
            xx += (x[i] - mx) * (x[i] - mx);
        }
        return xx > 0 ? xy / xx : 0;
    }
}
