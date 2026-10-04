package apincer.music.core.authenticity;
/**
 * Container for audio signal analysis results produced via FFmpeg-based
 * processing (astats + aspectralstats filters).
 *
 * <p>This class represents a snapshot of measurable audio characteristics
 * used for quality evaluation, fake lossless detection, and source
 * classification (e.g. MP3, AAC, FLAC, Hi-Res).</p>
 *
 * <h2>Overview</h2>
 * <p>The analysis focuses on three main domains:</p>
 * <ul>
 *     <li><b>Time-domain:</b> RMS, peak, dynamic range</li>
 *     <li><b>Frequency-domain:</b> spectral rolloff, flatness, entropy</li>
 *     <li><b>Band analysis:</b> high-frequency vs low-frequency energy</li>
 * </ul>
 *
 * <h2>Important Notes</h2>
 * <ul>
 *     <li>All decibel (dB) values follow logarithmic scale.</li>
 *     <li>Energy comparisons should convert dB → linear scale.</li>
 *     <li>Results are heuristic and may vary depending on audio content.</li>
 *     <li>Analysis typically uses a short segment (e.g. 20 seconds).</li>
 * </ul>
 */
public class AudioAnalysisResult {

    /**
     * Input sample rate in Hz (e.g. 44100, 48000, 96000).
     *
     * <p>Used to compute Nyquist frequency and detect upscaled audio.
     * Nyquist = sampleRate / 2.</p>
     */
    public int sampleRate;

    /**
     * Bit depth of the audio container (e.g. 16, 24).
     *
     * <p>Used for estimating dynamic range potential and detecting
     * fake high bit-depth files.</p>
     */
    public int bitDepth;

    /**
     * Root Mean Square (RMS) level in dBFS.
     *
     * <p>Represents the average signal energy over time.</p>
     */
    public double rms;

    /**
     * Peak level in dBFS.
     *
     * <p>Represents the maximum amplitude observed in the signal.
     * Values close to 0 dBFS may indicate clipping.</p>
     */
    public double peak;

    /**
     * Estimated noise floor in dBFS.
     *
     * <p>Represents the background noise level. Used to calculate
     * signal-to-noise ratio (SNR).</p>
     */
    public double noiseFloor;

    /**
     * Dynamic range in dB.
     *
     * <p>Typically computed as |peak - rms|.</p>
     * <p>Higher values indicate more dynamic (less compressed) audio.</p>
     */
    public double dynamicRange;

    /**
     * RMS level of the high-frequency band (above threshold, e.g. 18–22kHz).
     *
     * <p>Used to detect presence of high-frequency content and identify
     * lossy compression artifacts.</p>
     */
    public double highBandRms;

    /**
     * RMS level of the low-frequency band (below threshold).
     *
     * <p>Used together with {@link #highBandRms} to compute relative
     * energy distribution.</p>
     */
    public double lowBandRms;

    /**
     * High-frequency energy ratio (dimensionless).
     *
     * <p>Defined as:</p>
     * <pre>
     * hfRatio = E_high / (E_high + E_low)
     * </pre>
     *
     * <p>Where E is linear energy converted from dB.</p>
     *
     * <p>Typical values:</p>
     * <ul>
     *     <li>&lt; 0.05 → likely lossy (missing highs)</li>
     *     <li>0.05–0.10 → suspicious</li>
     *     <li>&gt; 0.10 → healthy high-frequency content</li>
     * </ul>
     */
    public double hfRatio;

    /**
     * Spectral rolloff frequency in Hz.
     *
     * <p>Represents the frequency below which a given percentage
     * (typically 85–95%) of total spectral energy is contained.</p>
     *
     * <p>Used as a proxy for cutoff frequency:</p>
     * <ul>
     *     <li>~16 kHz → MP3 (low bitrate)</li>
     *     <li>~19 kHz → AAC / high bitrate</li>
     *     <li>~20–22 kHz → true lossless</li>
     * </ul>
     */
    public double rolloff;

    /**
     * Spectral flatness (0.0 – 1.0).
     *
     * <p>Indicates how noise-like a signal is:</p>
     * <ul>
     *     <li>~0 → tonal (structured, harmonic)</li>
     *     <li>~1 → noise-like (random)</li>
     * </ul>
     *
     * <p>High flatness in high-frequency bands may indicate lossy artifacts.</p>
     */
   // public double spectralFlatness;

    /**
     * Spectral entropy (0.0 – 1.0).
     *
     * <p>Measures randomness or unpredictability of frequency distribution.</p>
     *
     * <p>Higher values indicate more uniform/noisy spectrum, often seen
     * in compressed audio.</p>
     */
    public double spectralEntropy;

    /**
     * Final classification verdict.
     *
     * <p>Examples:</p>
     * <ul>
     *     <li>"MPEG/Lossy Source"</li>
     *     <li>"Fake Lossless"</li>
     *     <li>"Verified Lossless"</li>
     *     <li>"Studio Master (Hi-Res)"</li>
     * </ul>
     */
    public String verdict;

    /**
     * Confidence score (0.0 – 1.0).
     *
     * <p>Represents how certain the classifier is about the verdict.</p>
     */
   // public double confidence;
}