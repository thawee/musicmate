package apincer.music.core.authenticity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Synthesized signals through {@link SpectrumAccumulator} and {@link SpectralCliffDetector}: about one
 * sine per 250 Hz band, falling 1.5 dB per kHz like a recording, cut where a resampler or encoder would cut.
 */
public class SpectralCliffDetectorTest {

    private static final double SECONDS = 4;

    private static SpectralCliffDetector.Finding analyze(int rate, int bits, double topHz, double imagesFromHz) {
        return analyze(rate, bits, topHz, imagesFromHz, false);
    }

    /**
     * Sines from 125 Hz up to {@code topHz}; {@code imagesFromHz} adds mirror images above 22.05 kHz;
     * {@code hump} adds a noise-like rise above 25 kHz, as DSD's noise shaping leaves in converted PCM.
     */
    private static SpectralCliffDetector.Finding analyze(int rate, int bits, double topHz, double imagesFromHz, boolean hump) {
        int n = (int) (rate * SECONDS);
        float[] x = new float[n];
        java.util.Random jitter = new java.util.Random(3);
        for (double grid = 125; grid < topHz; grid += 250) {
            // Off the grid a little, so the tones are not symmetric around 24 kHz by construction
            double f = Math.min(grid + (jitter.nextDouble() - 0.5) * 120, topHz - 1);
            addSine(x, rate, f, 0.01 * Math.pow(10, -1.5 * f / 1000 / 20));
            if (imagesFromHz > 0 && f >= imagesFromHz) {
                addSine(x, rate, 44100 - f, 0.001 * Math.pow(10, -1.5 * f / 1000 / 20));
            }
        }
        if (hump) {
            java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < 400; i++) {
                double f = 25000 + random.nextDouble() * 20000;
                addSine(x, rate, f, 0.0005 * Math.pow(10, 1.2 * (f - 25000) / 1000 / 20));
            }
        }
        SpectrumAccumulator spectrum = new SpectrumAccumulator(rate);
        for (float v : x) spectrum.add(v);
        return SpectralCliffDetector.classify(spectrum.bandsDb(), spectrum.binsDb(), rate, bits);
    }

    private static void addSine(float[] x, int rate, double f, double amplitude) {
        // Rotating phasor: cheaper than Math.sin per sample
        double c = Math.cos(2 * Math.PI * f / rate), s = Math.sin(2 * Math.PI * f / rate);
        double re = amplitude * Math.cos(f), im = amplitude * Math.sin(f);
        for (int i = 0; i < x.length; i++) {
            x[i] += (float) im;
            double t = re * c - im * s;
            im = re * s + im * c;
            re = t;
        }
    }

    @Test
    public void contentRunningPast22kHzShowsNoSignOfUpsampling() {
        assertEquals(SpectralCliffDetector.Kind.NO_SIGN_OF_UPSAMPLING, analyze(96000, 24, 40000, 0).kind);
    }

    @Test
    public void aBrickWallAt22kHzIsUpsampledFrom44k() {
        SpectralCliffDetector.Finding f = analyze(96000, 24, 22000, 0);
        assertEquals(SpectralCliffDetector.Kind.UPSAMPLED_FROM_44, f.kind);
        assertEquals(22000, f.edgeHz, 500);
    }

    @Test
    public void aBrickWallAt24kHzIsUpsampledFrom48k() {
        SpectralCliffDetector.Finding f = analyze(192000, 24, 23900, 0);
        assertEquals(SpectralCliffDetector.Kind.UPSAMPLED_FROM_48, f.kind);
        assertEquals(24000, f.edgeHz, 500);
    }

    @Test
    public void mirrorImagesAbove22kHzAreACheapResampler() {
        assertEquals(SpectralCliffDetector.Kind.UPSAMPLED_FROM_44_IMAGES, analyze(96000, 24, 22000, 12000).kind);
    }

    @Test
    public void aNoiseHumpRisingAbove25kHzIsNotImages() {
        assertEquals(SpectralCliffDetector.Kind.NO_SIGN_OF_UPSAMPLING, analyze(96000, 24, 40000, 0, true).kind);
    }

    @Test
    public void aCutoffNear17kHzInACdFileIsLossy() {
        SpectralCliffDetector.Finding f = analyze(44100, 16, 17300, 0);
        assertEquals(SpectralCliffDetector.Kind.LOSSY, f.kind);
        assertEquals(17300, f.edgeHz, 500);
    }

    @Test
    public void aCdFileRunningToItsLimitHasNoLossyCutoff() {
        assertEquals(SpectralCliffDetector.Kind.NO_LOSSY_CUTOFF, analyze(44100, 16, 21900, 0).kind);
    }

    @Test
    public void nothingAbove5kHzCannotBeJudged() {
        assertEquals(SpectralCliffDetector.Kind.CANT_TELL, analyze(192000, 24, 5000, 0).kind);
    }

    @Test
    public void bandLevelMatchesASinesPower() {
        // A full-scale-ish sine: its power A^2/2 spread over the band it falls in, as density
        int rate = 48000;
        SpectrumAccumulator spectrum = new SpectrumAccumulator(rate);
        double a = 0.5;
        for (int i = 0; i < rate * 2; i++) spectrum.add((float) (a * Math.sin(2 * Math.PI * 1125 * i / rate)));
        double[] db = spectrum.bandsDb();
        int band = (int) (1125 / SpectralCliffDetector.BAND_HZ);
        for (int b = 0; b < db.length; b++) {
            if (b != band) assertEquals("band " + b + " below the sine's", true, db[b] < db[band] - 30);
        }
    }
}
