package apincer.music.core.authenticity;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance audio engine designed to verify the authenticity and
 * technical integrity of digital audio files.
 *
 * <p>This analyzer utilizes {@code ffmpeg-kit} to perform deep bitstream audits,
 * detecting:
 * <ul>
 * <li><b>Fake Lossless:</b> MPEG/Lossy sources transcoded into FLAC/WAV containers.</li>
 * <li><b>Upscaled Content:</b> CD-quality files upsampled to Hi-Res (96kHz/192kHz).</li>
 * <li><b>Bit-Depth Integrity:</b> Verifying if 24-bit containers actually contain 24-bit dynamic range.</li>
 * <li><b>Mastering Quality:</b> Distinguishing between Studio Masters and Analog/Tape transfers.</li>
 * </ul>
 *
 * @author Thawee Prakaipetch
 * @version 2026.03.23
 */
public class AudioAuthenticityAnalyzer {

    private static final String TAG = "AudioAnalyzer";

    public interface Callback {
        void onResult(AudioAnalysisResult result);
        void onError(String error);
    }

    /**
     * Executes a technical audit on the specified audio file.
     * * <p>The process runs asynchronously, utilizing input seeking to analyze a
     * 20-second window (starting at 30s) to balance speed and accuracy on mobile hardware.
     *
     * @param inputPath  Absolute path to the audio file.
     * @param sampleRate The nominal sample rate of the file (e.g., 44100, 96000).
     * @param bitDepth   The nominal bit depth (e.g., 16, 24).
     * @param callback   The listener for async results.
     */
    public static void analyze(String inputPath,
                                int sampleRate,
                                int bitDepth,
                                Callback callback) {

        StringBuilder filters = new StringBuilder();

        if (sampleRate > 44100) {
            filters.append("-filter_complex \"");
            filters.append("[0:a]asplit=5[a][b][c][d][e];[a]astats=metadata=1:reset=1[a_full];");
            filters.append("[b]lowpass=f=16000,astats=metadata=1:reset=2[a_16k];");
            filters.append("[c]highpass=f=16000,lowpass=f=20000,astats=metadata=1:reset=3[a_16_20];");
            filters.append("[d]highpass=f=20000,lowpass=f=24000,astats=metadata=1:reset=4[a_20_24];");
            filters.append("[e]highpass=f=24000,astats=metadata=1:reset=5[a_24k];");
            filters.append("\" ");
            filters.append("-map \"[a_full]\" -f null - ");
            filters.append("-map \"[a_16k]\" -f null - ");
            filters.append("-map \"[a_16_20]\" -f null - ");
            filters.append("-map \"[a_20_24]\" -f null - ");
            filters.append("-map \"[a_24k]\" -f null - ");
        } else {
            filters.append("-filter_complex \"");
            filters.append("[0:a]asplit=3[a][b][c];[a]astats=metadata=1:reset=1[a_full];");
            filters.append("[b]lowpass=f=16000,astats=metadata=1:reset=2[a_16k];");
            filters.append("[c]highpass=f=16000,lowpass=f=20000,astats=metadata=1:reset=3[a_16_20];");
            filters.append("\" ");
            filters.append("-map \"[a_full]\" -f null - ");
            filters.append("-map \"[a_16k]\" -f null - ");
            filters.append("-map \"[a_16_20]\" -f null - ");
        }

        String command =
                "-hide_banner -loglevel info " +
                        "-ss 30 -t 60 " +
                        "-i \"" + inputPath + "\" " +
                        filters;

                       // "[0:a]asplit=5[a][b][c][d][e];" +

                        // full
                       // "[a]astats=metadata=1:reset=1[a_full];" +

                        // bands
                       /* "[b]lowpass=f=16000,astats=metadata=1:reset=2[a_16k];" +
                        "[c]highpass=f=16000,lowpass=f=20000,astats=metadata=1:reset=3[a_16_20];" +
                        "[d]highpass=f=20000,lowpass=f=24000,astats=metadata=1:reset=4[a_20_24];" +
                        "[e]highpass=f=24000,astats=metadata=1:reset=5[a_24k];" + */

        FFmpegKit.executeAsync(command, session -> {

            if (!ReturnCode.isSuccess(session.getReturnCode())) {
                callback.onError("FFmpeg analysis failed");
                return;
            }

            String log = session.getOutput();

            AudioAnalysisResult r = new AudioAnalysisResult();

            r.sampleRate = sampleRate;
            r.bitDepth = bitDepth;

            // 1. BASELINE (Full Signal) - Always use Parsed_astats_1
            r.rms = parseValue(log, "Parsed_astats_1", "Overall", "RMS level dB");
            r.peak = parseValue(log, "Parsed_astats_1", "Overall", "Peak level dB");
            r.noiseFloor = parseValue(log, "Parsed_astats_1", "Overall", "Noise floor dB");
            r.spectralEntropy = parseValue(log, "Parsed_astats_1", "Overall", "Entropy");

            // 2. BANDS - Targeted using their Filter IDs
            double rms16   = parseValue(log, "Parsed_astats_3", "Overall", "RMS level dB");
            double rms1620 = parseValue(log, "Parsed_astats_6", "Overall", "RMS level dB");
            double rms2024 = parseValue(log, "Parsed_astats_9", "Overall", "RMS level dB");
            double rms24   = parseValue(log, "Parsed_astats_11", "Overall", "RMS level dB");

            // Use the noise floor of the WHOLE file as the reference

            // Use the audible band (0-16kHz) as the baseline for what "Music" sounds like
            double musicBase = rms16;

            // If a band is 40dB quieter than the music, it's considered "Cut off"
            double cutoffThreshold = musicBase - 40.0;

            if (rms1620 < cutoffThreshold) {
                r.rolloff = 16000;
            } else if (rms2024 < cutoffThreshold) {
                r.rolloff = 20000;
            } else if (rms24 < cutoffThreshold) {
                r.rolloff = 24000;
            } else {
                r.rolloff = r.sampleRate / 2.0; // Full 48kHz (for 96kHz file)
            }

            double e16   = dbToEnergy(rms16);
            double e1620 = dbToEnergy(rms1620);
            double e2024 = dbToEnergy(rms2024);
            double e24   = dbToEnergy(rms24);

            double hfEnergy = e2024 + e24;
            double totalEnergy = e16 + e1620 + e2024 + e24;

           // r.hfRatio = hfEnergy / totalEnergy;
            r.hfRatio = totalEnergy > 1e-12 ? hfEnergy / totalEnergy : 0.0;

            // 4. MATH & CLASSIFY
            r.dynamicRange = Math.abs(r.peak - r.rms);
            classify(r, rms16, rms1620, rms2024, rms24);

            callback.onResult(r);

        });
    }

    /**
     * Parses a specific numeric metric from a filtered block in the FFmpeg log output.
     *
     * @param log      The full output log from FFmpeg.
     * @param filterId The internal filter identifier (e.g., Parsed_astats_1).
     * @param section  The log section (e.g., Overall).
     * @param key      The metric key (e.g., RMS level dB).
     * @return The parsed double value, or -144.0 if infinite, or NaN if not found.
     */
    private static double parseValue(String log, String filterId, String section, String key) {
        // This regex:
        // 1. Finds the filter name (e.g., Parsed_astats_1)
        // 2. Skips text until it finds the section (e.g., Overall)
        // 3. Grabs the key: value
        Pattern p = Pattern.compile(
                filterId + "\\s.*?" + section + ".*?" + key + ":\\s*(-?\\d+\\.?\\d*|-inf)",
                Pattern.DOTALL);

        Matcher m = p.matcher(log);
        if (m.find()) {
            String val = m.group(1);
            if ("-inf".equalsIgnoreCase(val)) return -144.0;
            return Double.parseDouble(val);
        }
        return Double.NaN;
    }

    private static double dbToEnergy(double db) {
        return Math.pow(10.0, db / 10.0);
    }

    /**
     * Executes a multi-signal heuristic analysis to determine the authenticity
     * and origin of the audio bitstream.
     * * <p>The classification engine uses a weighted scoring model to distinguish between
     * native high-resolution masters, upscaled CD-quality content, and transcoded
     * lossy sources (MPEG). It evaluates spectral rolloff, high-frequency energy ratios,
     * signal-to-noise ratios in ultrasonic bands, and spectral entropy.</p>
     * * <h3>Scoring Metrics:</h3>
     * <ul>
     * <li><b>Hard Cutoff (+2):</b> Triggered if the spectral rolloff is below 80%
     * of the Nyquist frequency, suggesting an encoder-imposed limit.</li>
     * <li><b>Weak HF (+2):</b> Triggered if high-frequency energy accounts for
     * less than 6% of the total signal energy.</li>
     * <li><b>Low Clarity (+1):</b> Triggered if the signal in the ultrasonic
     * bands is less than 10dB above the noise floor.</li>
     * <li><b>Fake HF (+1):</b> Triggered if high entropy (>0.85) is detected in
     * weak high-frequency bands, indicating noise-shaped dither or artifacts.</li>
     * <li><b>Energy Gap (+1):</b> Triggered for Hi-Res containers if the audible
     * base (16kHz) is >40dB louder than the ultrasonic peak (24kHz).</li>
     * </ul>
     * * @param r      The {@link AudioAnalysisResult} object to populate with the verdict.
     * @param r16    The RMS level (dB) of the 0-16kHz band.
     * @param r1620  The RMS level (dB) of the 16-20kHz band.
     * @param r2024  The RMS level (dB) of the 20-24kHz band.
     * @param r24    The RMS level (dB) of the >24kHz band.
     */
    private static void classify(AudioAnalysisResult r,
                                 double r16,
                                 double r1620,
                                 double r2024,
                                 double r24) {

        double nyquist = r.sampleRate / 2.0;
        double rolloffRatio = r.rolloff / nyquist;

        //r2024 = safeDb(r2024);
        //r24   = safeDb(r24);
       // r.noiseFloor = safeDb(r.noiseFloor);
        double snrHigh = (r2024 + r24) / 2.0 - r.noiseFloor;

        // --- Signals
        boolean hardCutoff = rolloffRatio < 0.80;
        boolean weakHF     = r.hfRatio < 0.06;
        boolean lowClarity = snrHigh < 10.0;

        // fake HF (noisy reconstruction)
        boolean fakeHF =
                r.hfRatio < 0.10 &&
                        r.spectralEntropy > 0.85;

        // --- Scoring model (robust)
        int score = 0;

        if (hardCutoff) score += 2;
        if (weakHF) score += 2;
        if (lowClarity) score += 1;
        if (fakeHF) score += 1;

        double gap = r16 - r24; // Base energy vs ultrasonic energy
        if (r.sampleRate > 48000 && gap > 40.0) {
            score += 1; // Add weight if the ultrasonic range is significantly quieter than audible
        }

       // boolean isLossy = score >= 3;

        // --- Verdict
        /*if (isLossy) {
            r.verdict = "MPEG/Lossy Source";
        }
        else */
        if(score >= 3) {
            if (r.sampleRate > 48000 && (rolloffRatio < 0.85 || gap > 40.0)) {
                r.verdict = "Upscaled Content";
            }else {
                r.verdict = "MPEG/Lossy Source";
            }
        }
        else if (r.sampleRate > 48000) {
            if (snrHigh > 15.0) {
                r.verdict = "Studio Master (Native Hi-Res)";
            } else if (snrHigh > 5.0) {
                r.verdict = "Analog Master (Hi-Res Rip)";
            } else {
                r.verdict = "Hi-Res (Noisy/Dithered)";
            }
        }
        else if (rolloffRatio > 0.90 && r.hfRatio > 0.10) {
            r.verdict = "Verified Lossless";
        }
        else {
            r.verdict = "Likely CD Quality";
        }

        // --- Clipping
        if (r.peak >= -0.01) {
            r.verdict += " [Clipped]";
        }
    }

    /**
     * Classifies an analyzed audio track into quality/source categories such as
     * Lossy (MP3/AAC), Fake Lossless (transcoded), True Lossless, or Hi-Res.
     *
     * <p>This method uses a multi-signal heuristic model combining spectral,
     * dynamic, and high-frequency energy analysis derived from FFmpeg filters
     * (astats + aspectralstats).</p>
     *
     * <h3>Core Detection Signals</h3>
     * <ul>
     *     <li><b>Spectral Rolloff:</b> Approximates cutoff frequency. Lower values
     *     indicate lossy compression (e.g. MP3 ~16kHz).</li>
     *
     *     <li><b>High-Frequency Energy Ratio (HF Ratio):</b> Measures energy above
     *     a threshold (e.g. 18–22kHz) relative to total signal energy. Lossy sources
     *     typically have very low HF energy.</li>
     *
     *     <li><b>High-Band Signal-to-Noise Ratio (SNR):</b> Evaluates clarity of
     *     high-frequency content. Low SNR suggests compression artifacts or noise.</li>
     *
     *     <li><b>Spectral Flatness & Entropy:</b> Detects unnatural frequency
     *     distributions. Lossy encoders often produce “flat” or overly uniform
     *     high-frequency noise.</li>
     * </ul>
     *
     * <h3>Detection Logic</h3>
     * <ul>
     *     <li><b>Hard Cutoff:</b> Rolloff below ~80% of Nyquist frequency strongly
     *     indicates lossy compression.</li>
     *
     *     <li><b>Weak HF Energy:</b> HF ratio below ~0.05 suggests missing high
     *     frequencies (typical of MP3/AAC).</li>
     *
     *     <li><b>Low Clarity:</b> Poor SNR in high-frequency band indicates degraded
     *     or synthetic content.</li>
     *
     *     <li><b>Fake High Frequencies:</b> High flatness + entropy combined with
     *     low HF energy suggests artificially reconstructed highs.</li>
     * </ul>
     *
     * <h3>Verdict Categories</h3>
     * <ul>
     *     <li><b>MPEG/Lossy Source:</b> Strong indicators of lossy encoding or
     *     transcoded content.</li>
     *
     *     <li><b>Upscaled Content:</b> High sample rate but lacking true high-frequency
     *     extension (likely upsampled).</li>
     *
     *     <li><b>Studio Master (Native Hi-Res):</b> Clean, high SNR high-frequency
     *     content typical of digital masters.</li>
     *
     *     <li><b>Analog Master (Hi-Res Rip):</b> Natural high-frequency noise
     *     (tape/vinyl hiss) with moderate SNR.</li>
     *
     *     <li><b>Verified Lossless:</b> Strong high-frequency presence with no lossy
     *     artifacts.</li>
     *
     *     <li><b>Likely CD Quality:</b> Standard 44.1kHz lossless content without
     *     extended high-frequency range.</li>
     * </ul>
     *
     * <h3>Additional Notes</h3>
     * <ul>
     *     <li>Analysis assumes precomputed values from FFmpeg filters:
     *     {@code astats} and {@code aspectralstats}.</li>
     *
     *     <li>All dB values are converted to linear energy where required to ensure
     *     physically meaningful comparisons.</li>
     *
     *     <li>Results are heuristic and may vary for edge cases such as:
     *         <ul>
     *             <li>Vinyl or tape recordings</li>
     *             <li>Low-pass filtered masters</li>
     *             <li>Very quiet or sparse audio segments</li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <h3>Side Effects</h3>
     * <ul>
     *     <li>Sets {@link AudioAnalysisResult#verdict}</li>
     *     <li>Appends "[Clipped]" if peak level indicates clipping</li>
     * </ul>
     *
     * @param r The populated {@link AudioAnalysisResult} containing spectral,
     *          dynamic, and band-limited measurements for the audio track.
     */
    private static void classify(AudioAnalysisResult r) {

        double nyquist = r.sampleRate / 2.0;
        double rolloffRatio = r.rolloff / nyquist;

        // --- Energy-based HF ratio (FIXED)
        double highEnergy = Math.pow(10.0, r.highBandRms / 10.0);
        double lowEnergy  = Math.pow(10.0, r.lowBandRms / 10.0);
        double hfRatio = highEnergy / (highEnergy + lowEnergy);

        double snrHighBand = r.highBandRms - r.noiseFloor;

        // --- Core Signals
        boolean hardCutoff = rolloffRatio < 0.80;
        boolean weakHF = hfRatio < 0.05;
        boolean lowClarity = snrHighBand < 10.0;
        boolean fakeHF = weakHF && lowClarity;

        boolean fakeHighFreq =
      //          r.spectralFlatness > 0.35 &&
                        r.spectralEntropy  > 0.85 &&
                        hfRatio < 0.08;

        // --- Weighted decision (better than boolean OR)
        int score = 0;

        if (hardCutoff) score += 2;
        if (fakeHF) score += 2;
        if (lowClarity) score += 1;
        if (fakeHighFreq) score += 1;

        boolean isLossy = score >= 3;

        // --- Verdict
        if (isLossy) {
            r.verdict = "MPEG/Lossy Source";
        }
        else if (r.sampleRate > 48000 && rolloffRatio < 0.85) {
            r.verdict = "Upscaled Content";
        }
        else if (r.sampleRate > 48000) {

            if (snrHighBand > 15.0) {
                r.verdict = "Studio Master (Native Hi-Res)";
            } else if (snrHighBand > 5.0) {
                r.verdict = "Analog Master (Hi-Res Rip)";
            } else {
                r.verdict = "Hi-Res (Noisy/Dithered)";
            }
        }
        else if (rolloffRatio > 0.90 && hfRatio > 0.10) {
            r.verdict = "Verified Lossless";
        }
        else {
            r.verdict = "Likely CD Quality";
        }

        // --- Clipping
        if (r.peak >= -0.01) {
            r.verdict += " [Clipped]";
        }
    }

}