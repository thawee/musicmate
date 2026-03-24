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

        //String freqFilter = sampleRate > Constants.QUALITY_SAMPLING_RATE_44?"21000":"18000";
        // Professional Quality Detection Logic
        String freqFilter;
        if (sampleRate <= 44100) {
            // 18.5kHz is the "Danger Zone" for MP3/AAC.
            // True Lossless stays strong here; Lossy starts the 'cliff' dive.
            freqFilter = "18500";
        } else if (sampleRate <= 48000) {
            // For 48kHz (Studio/DVD): Content above 20kHz is the indicator of quality.
            freqFilter = "20000";
        } else {
            // For Hi-Res (96k/192k): Content MUST exist above the CD-limit (22.05kHz).
            // This is the "Truth Line" for upsampled files.
            freqFilter = "22050";
        }

        String command =
                "-hide_banner -loglevel info " +
                        "-ss 30 -t 20 " +
                        "-i \"" + inputPath + "\" " +
                        "-filter_complex \"" +
                        "[0:a]asplit=4[a][b][c][d];" +

                        "[a]astats=metadata=1:reset=0[a_full];" +

                        "[b]highpass=f="+freqFilter+",astats=metadata=1:reset=0[a_high];" +

                        "[c]lowpass=f="+freqFilter+",astats=metadata=1:reset=0[a_low];" +

                        "[d]aspectralstats=measure=flatness+entropy[a_spec]" +
                        "\" " +

                        "-map \"[a_full]\" -f null - " +
                        "-map \"[a_high]\" -f null - " +
                        "-map \"[a_low]\" -f null - " +
                        "-map \"[a_spec]\" -f null -";

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
            //r.noiseFloor = parseValue(log, "Parsed_astats_1", "Overall", "Noise floor dB");

            // 2. BANDS - Targeted using their Filter IDs
            // [b]highpass -> Parsed_astats_3
            r.highBandRms = parseValue(log, "Parsed_astats_3", "Overall", "RMS level dB");
            r.noiseFloor = parseValue(log, "Parsed_astats_3", "Overall", "Noise floor dB");

            // [c]lowpass -> Parsed_astats_5
            r.lowBandRms = parseValue(log, "Parsed_astats_5", "Overall", "RMS level dB");

            // 3. SPECTRAL (Fixes the NaN issue)
            // aspectralstats doesn't have an "Overall" section, so we average the channels
            r.spectralFlatness = parseAverageSpectral(log, "flatness");
            r.spectralEntropy = parseAverageSpectral(log, "entropy");

            // 4. MATH & CLASSIFY
            r.dynamicRange = Math.abs(r.peak - r.rms);
            classify(r);

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
                filterId + ".*?" + section + ".*?" + key + ":\\s*(-?\\d+\\.?\\d*|-inf)",
                Pattern.DOTALL);

        Matcher m = p.matcher(log);
        if (m.find()) {
            String val = m.group(1);
            if ("-inf".equalsIgnoreCase(val)) return -144.0;
            return Double.parseDouble(val);
        }
        return Double.NaN;
    }

    private static double parseAverageSpectral(String log, String key) {
        // Regex matches "flatness: 0.123" anywhere in the log
        Pattern p = Pattern.compile(key + ":\\s*(-?\\d+\\.?\\d*)");
        Matcher m = p.matcher(log);

        double total = 0;
        int count = 0;
        while (m.find()) {
            total += Double.parseDouble(m.group(1));
            count++;
        }
        return (count > 0) ? (total / count) : Double.NaN;
    }

    /**
     * Analyzes the relationship between various metrics to determine the final
     * authenticity verdict.
     * * <p>Key Logic Paths:
     * <ul>
     * <li><b>Entropy > 0.85:</b> High disorder in high-frequencies indicates MPEG noise artifacts.</li>
     * <li><b>highVsFull < -65dB:</b> Total lack of energy above 22kHz in a 96kHz file proves CD upscaling.</li>
     * <li><b>Noise Floor > -91dB:</b> A 24-bit file that does not exceed 16-bit theoretical dynamic range.</li>
     * </ul>
     *
     * @param r The result object to update with the classification verdict.
     */
    private static void classify(AudioAnalysisResult r) {
        // 1. Preparation & Variables
        double highVsFull = r.highBandRms - r.rms;
        double snrHighBand = r.highBandRms - r.noiseFloor;

        boolean weakHF = highVsFull < -55.0;
        boolean lowClarity = snrHighBand < 10.0;

        boolean fakeHighFreq = false;

        if (r.sampleRate <= 48000) {
            fakeHighFreq =
                    r.spectralFlatness > 0.35 &&
                            r.spectralEntropy  > 0.85 &&
                            r.highBandRms < -60.0 &&
                            snrHighBand < 12.0;
        }

        // Final lossy decision
        boolean isLossy =
                (weakHF && lowClarity)   // classic MP3 cutoff
                        || fakeHighFreq;         // modern "noisy HF" MP3

        // 1. The "Shelf" check (Improved)
        // If the high band (>18.5k) is 45dB quieter than the full range,
        // and the SNR is low, it's almost certainly a 128-192kbps transcode.
        //if (r.sampleRate <= 48000 && r.peak >= 0.0 && r.noiseFloor > -75.0) {
        //    isLossy = true;
        //}
        //Overshoot (secondary signal only)
        if (r.sampleRate <= 48000 &&
                r.peak >= 0.0 &&
                r.noiseFloor > -75.0 &&
                weakHF) {
            isLossy = true;
        }

        if (isLossy) {
            r.verdict = "MPEG/Lossy Source";
        }
        else if (r.sampleRate > 48000 && highVsFull < -65.0) {
            r.verdict = "Upscaled Content";
        }
        else if (r.sampleRate > 48000 && highVsFull > -60.0) {
            if (snrHighBand > 15.0) { // Higher SNR = Clean Digital
                r.verdict = "Studio Master (Native Hi-Res)";
            } else if (snrHighBand > 5.0) { // Lower SNR = Natural Tape Hiss
                r.verdict = "Analog Master (Hi-Res Rip)";
            } else {
                r.verdict = "Hi-Res (Noisy/Dithered)";
            }
        }
        else if (r.sampleRate <= 44100) {
            r.verdict = "Likely CD Quality";
        }
        else {
            r.verdict = "Verified Lossless";
        }

        // 4. Deep Bit-Depth Analysis (The Truth Check)
        // If the container is 24-bit, we check if the noise floor justifies it.
        /*if (r.bitDepth >= 24) {
            if (r.noiseFloor > -91.0) {
                // If noise floor is > -91dB, it's effectively 16-bit dynamic range
                r.verdict += " (16-bit DR)";
            //} else if (r.noiseFloor <= -110.0) {
            } else if (r.noiseFloor <= -105.0) {
                // strong 24-bit candidate
                // Real 24-bit performance
                r.verdict += " (True 24-bit DR)";
            }
        } */

        // 5. Clipping Alert
        if (r.peak >= -0.01) {
            r.verdict += " [Clipped]";
        }
    }

    private static void classify2(AudioAnalysisResult r) {
        // 1. Preparation & Variables
        double highToLowRatio = r.highBandRms - r.lowBandRms;
        // double highVsFull = r.highBandRms - r.rms;
        double snrHighBand = r.highBandRms - r.noiseFloor;
        boolean isLossy = false;

        // 2. The "Lossy Fingerprint" (Shelf Detection)
        // Lossy encoders (MP3/AAC) usually have a brick-wall filter at 16-20kHz.
        // This causes a massive drop in the High Band (>21kHz).
        //if (highToLowRatio < -40.0 && snrHighBand < 11.0) {

        // 1. The "Shelf" check (Improved)
        // If the high band (>18.5k) is 45dB quieter than the full range,
        // and the SNR is low, it's almost certainly a 128-192kbps transcode.
        if (highToLowRatio < -45.0 && snrHighBand < 10.0) {
            isLossy = true;
        }

        // Overshoot/Clipping detection (Common in bad transcodes)
        // If it's a standard sample rate but peaks are over 0dB with high noise
        if (r.sampleRate <= 48000 && r.peak >= 0.0 && r.noiseFloor > -75.0) {
            isLossy = true;
        }

        // 3. The Main Verdict Chain
        if (isLossy) {
            r.verdict = "MPEG/Lossy Source";
        }
        // If user thinks it's 96k/192k but the 'a_high' (above 22.05k) is empty.
        else if (r.sampleRate > 48000 && r.highBandRms < -90.0) {
            //else if (r.sampleRate > 48000 && highToLowRatio < -65.0) {
            // High Sample rate (96k+) but no energy in the ultrasonic range
            r.verdict = "Upscaled Content";
        }
        else if (r.sampleRate > 44100 && highToLowRatio > -50.0) {
            // Real energy exists above 21kHz
            if (snrHighBand > 15.0) {
                r.verdict = "Studio Master";
            } else {
                // High energy but close to noise floor (common in Vinyl rips)
                r.verdict = "Analog Master";
            }
        }
        else if (r.sampleRate <= 44100) {
            r.verdict = "Likely CD Quality";
        }
        else {
            r.verdict = "Verified Lossless";
        }

        // 4. Deep Bit-Depth Analysis (The Truth Check)
        // If the container is 24-bit, we check if the noise floor justifies it.
        if (r.bitDepth >= 24) {
            if (r.noiseFloor > -91.0) {
                // If noise floor is > -91dB, it's effectively 16-bit dynamic range
                r.verdict += " (16-bit DR)";
                //} else if (r.noiseFloor <= -110.0) {
            } else if (r.noiseFloor <= -105.0) {
                // strong 24-bit candidate
                // Real 24-bit performance
                r.verdict += " (True 24-bit DR)";
            }
        }

        // 5. Clipping Alert
        if (r.peak >= -0.01) {
            r.verdict += " [Clipped]";
        }
    }
}