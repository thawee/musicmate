package apincer.music.core.authenticity;

/**
 * Average power spectrum of a mono signal (Welch's method: 8192-point Hann frames, half overlapping),
 * reported in {@link SpectralCliffDetector#BAND_HZ}-wide bands as power spectral density in dB.
 * Samples are fed one at a time, so a long decode never has to sit in memory.
 */
public final class SpectrumAccumulator {

    static final int SIZE = 8192;
    private static final int HOP = SIZE / 2;

    private final int sampleRate;
    private final double[] window = new double[SIZE];
    private final double windowPower;
    private final float[] ring = new float[SIZE];
    private final double[] re = new double[SIZE];
    private final double[] im = new double[SIZE];
    private final double[] power = new double[SIZE / 2 + 1];
    private final double[] cos = new double[SIZE / 2];
    private final double[] sin = new double[SIZE / 2];
    private long samples;
    private int frames;

    public SpectrumAccumulator(int sampleRate) {
        this.sampleRate = sampleRate;
        double sum = 0;
        for (int i = 0; i < SIZE; i++) {
            // Symmetric Hann, as numpy.hanning
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (SIZE - 1));
            sum += window[i] * window[i];
        }
        windowPower = sum;
        for (int i = 0; i < SIZE / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / SIZE);
            sin[i] = Math.sin(-2 * Math.PI * i / SIZE);
        }
    }

    public void add(float sample) {
        ring[(int) (samples % SIZE)] = sample;
        samples++;
        if (samples >= SIZE && (samples - SIZE) % HOP == 0) {
            frame();
        }
    }

    public int frames() {
        return frames;
    }

    /** Band levels in dB, band i covering [i, i + 1) x BAND_HZ, up to the Nyquist frequency. */
    public double[] bandsDb() {
        double bandHz = SpectralCliffDetector.BAND_HZ;
        int bands = (int) ((sampleRate / 2.0) / bandHz);
        double[] sum = new double[bands];
        int[] count = new int[bands];
        double scale = 1.0 / (Math.max(frames, 1) * sampleRate * windowPower);
        for (int k = 0; k < power.length; k++) {
            int band = (int) (k * (double) sampleRate / SIZE / bandHz);
            if (band < bands) {
                sum[band] += power[k] * scale;
                count[band]++;
            }
        }
        double[] db = new double[bands];
        for (int b = 0; b < bands; b++) {
            db[b] = 10 * Math.log10((count[b] > 0 ? sum[b] / count[b] : 0) + 1e-30);
        }
        return db;
    }

    private void frame() {
        long first = samples - SIZE;
        for (int i = 0; i < SIZE; i++) {
            re[i] = ring[(int) ((first + i) % SIZE)] * window[i];
            im[i] = 0;
        }
        fft(re, im);
        for (int k = 0; k <= SIZE / 2; k++) {
            power[k] += re[k] * re[k] + im[k] * im[k];
        }
        frames++;
    }

    /** In-place iterative radix-2 FFT of length SIZE. */
    private void fft(double[] re, double[] im) {
        for (int i = 1, j = 0; i < SIZE; i++) {
            int bit = SIZE >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= SIZE; len <<= 1) {
            int step = SIZE / len;
            for (int i = 0; i < SIZE; i += len) {
                for (int k = 0; k < len / 2; k++) {
                    double wr = cos[k * step], wi = sin[k * step];
                    int a = i + k, b = a + len / 2;
                    double xr = re[b] * wr - im[b] * wi;
                    double xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                }
            }
        }
    }
}
