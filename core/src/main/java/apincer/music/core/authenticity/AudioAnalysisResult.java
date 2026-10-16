package apincer.music.core.authenticity;

/** What Verify Lossless found in a track ({@link AudioAuthenticityAnalyzer}). */
public class AudioAnalysisResult {

    /** Sample rate of the decoded audio, in Hz. */
    public int sampleRate;

    /** Bit depth of the file, as tagged (16 assumed when unknown). */
    public int bitDepth;

    /** Highest sample level in the analyzed window, in dBFS (NaN when not measured). */
    public double peak;

    /** RMS level of the analyzed window over both channels, in dBFS (NaN when not measured). */
    public double rms;

    /** Where the spectrum falls off a cliff, in Hz; NaN when there is no cliff. */
    public double cutoffHz;

    /** The finding behind {@link #verdict}; null when the file was not checked. */
    public SpectralCliffDetector.Kind kind;

    /** One line for the user, for example "Upsampled from 44.1 kHz" or "No sign of upsampling". */
    public String verdict;
}
