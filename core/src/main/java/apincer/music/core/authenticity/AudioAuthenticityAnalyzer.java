package apincer.music.core.authenticity;

import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioAuthenticityAnalyzer {

    private static final String TAG = "AudioAnalyzer";

    public interface Callback {
        void onResult(AudioAnalysisResult result);
        void onError(String error);
    }

    public static void analyze(String inputPath,
                               int sampleRate,
                               int bitDepth,
                               Callback callback) {

        String command =
                "-hide_banner -loglevel info " +
                        "-ss 30 -t 20 " +
                        "-i \"" + inputPath + "\" " +
                        "-filter_complex \"" +
                        "[0:a]asplit=4[a][b][c][d];" +

                        "[a]astats=metadata=1:reset=0[a_full];" +

                        "[b]highpass=f=21000,astats=metadata=1:reset=0[a_high];" +

                        "[c]lowpass=f=21000,astats=metadata=1:reset=0[a_low];" +

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

/*
            r.rms = parseOverall(log, "RMS level dB");
            r.peak = parseOverall(log, "Peak level dB");
            r.noiseFloor = parseOverall(log, "Noise floor dB");

            r.highBandRms = parseFilter(log, 3, "RMS level dB"); // highpass
            r.lowBandRms  = parseFilter(log, 5, "RMS level dB"); // lowpass
            */

            // Assuming [a_full] = stats_1, [a_high] = stats_3, [a_low] = stats_5
           /* r.rms = parseFullSignalStats(log, "RMS level dB");
            r.peak = parseFullSignalStats(log, "Peak level dB");
            r.noiseFloor = parseFullSignalStats(log, "Noise floor dB");

            r.highBandRms = parseFilter(log, 3, "RMS level dB");
            r.lowBandRms  = parseFilter(log, 5, "RMS level dB");
            */

            /*
            r.spectralFlatness = parseSpectral(log, "flatness");
            r.spectralEntropy = parseSpectral(log, "entropy");

            r.dynamicRange = Math.abs(r.peak - r.rms);
            */

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
     * Specifically finds a value within a named filter block and a specific section.
     * @param filterId The FFmpeg filter name (e.g., "Parsed_astats_1")
     * @param section  "Overall" or "Channel: 1"
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

    // 1. Updated parseFilter to handle "-inf" and specific blocks
    private static double parseFilter(String log, int filterId, String key) {
        // Matches the block for "Parsed_astats_X" and looks for the key inside it
        // Added (?:-inf|...) to handle the infinite low signal cases
        Pattern p = Pattern.compile(
                "Parsed_astats_" + filterId + ".*?" + key + ":\\s*(-?\\d+\\.?\\d*|-inf)",
                Pattern.DOTALL);

        Matcher m = p.matcher(log);
        if (m.find()) {
            String val = m.group(1);
            if ("-inf".equalsIgnoreCase(val)) return -144.0; // Treat -inf as digital silence
            return Double.parseDouble(val);
        }
        return Double.NaN;
    }

    private static double parseFilterBak(String log, int filterId, String key) {

        Pattern p = Pattern.compile(
                "Parsed_astats_" + filterId + ".*?" + key + ":\\s*(-?\\d+\\.?\\d*)",
                Pattern.DOTALL);

        Matcher m = p.matcher(log);

        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }

        return Double.NaN;
    }

    private static double parseOverall(String log, String key) {
        // This regex EXPLICITLY looks for the 'Overall' block
        // and then finds the key within that specific context.
        Pattern p = Pattern.compile(
                "Overall\n(?:.*\n)*?.*?" + key + ":\\s*(-?\\d+\\.?\\d*)",
                Pattern.MULTILINE);

        Matcher m = p.matcher(log);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return Double.NaN;
    }

    private static double parseSpectral(String log, String key) {
        // aspectralstats outputs as "Channel X > key: value"
        // We will find all values and return the average of both channels.
        Pattern p = Pattern.compile(key + ":\\s*(-?\\d+\\.?\\d*)");
        Matcher m = p.matcher(log);

        double sum = 0;
        int count = 0;
        while (m.find()) {
            sum += Double.parseDouble(m.group(1));
            count++;
        }
        return (count > 0) ? (sum / count) : Double.NaN;
    }

    // 3. Force "Overall" to specifically look at astats_1 (the full signal)
    private static double parseFullSignalStats(String log, String key) {
        return parseFilter(log, 1, key);
    }

    private static double parseSpectralBak(String log, String key) {

        Pattern p = Pattern.compile(
                key + "\\s*:\\s*(-?\\d+\\.?\\d*)"
        );

        Matcher m = p.matcher(log);

        if (m.find())
            return Double.parseDouble(m.group(1));

        return Double.NaN;
    }

    private static void classify(AudioAnalysisResult r) {
        // 1. Calculate the 'Air' Quality
        // Real music has a gap between the signal and the noise floor.
        // Lossy encoders cut the signal so close to the noise floor that SNR becomes very low.
        double highToLowRatio = r.highBandRms - r.lowBandRms;
        double snrHighBand = r.highBandRms - r.noiseFloor;

        // 2. Identify the "Lossy Fingerprint"
        boolean isLossy = false;

        // Check A: The 'Shelf' Detection
        // If the energy drop is significant (>40dB) AND the signal is barely louder than noise (<11dB)
        if (highToLowRatio < -40.0 && snrHighBand < 11.0) {
            isLossy = true;
        }

       // if (r.highBandRms > -50.0) return 100; // Strong Hi-Res presence
       // if (r.highBandRms > -70.0) return 85;  // Standard CD Quality
       // if (r.highBandRms > -85.0) return 60;  // Likely 320kbps Upscale
       // if (r.highBandRms > -100.0) return 30; // Likely 128kbps Upscale

        // Check B: The 'Overshoot' Detection
        // Lossy encoders often cause peaks to exceed 0dB (Clipped).
        // If it's 44.1k, clipped, and has a high noise floor, it's almost certainly an MP3.
        if (r.sampleRate <= 48000 && r.peak >= 0.01 && r.noiseFloor > -75.0) {
            isLossy = true;
        }

        // 3. The Verdict Chain
        if (isLossy) {
            r.verdict = "Lossy Transcode (AAC/MP3)";
        }
        else if (r.sampleRate > 48000 && highToLowRatio < -65.0) {
            r.verdict = "Upsampled Hi-Res";
        }
        else if (r.sampleRate > 44100 && highToLowRatio > -50.0) {
            // High sample rate with real energy above 21kHz
            if (snrHighBand > 15.0) {
                r.verdict = "Genuine Hi-Res";
            } else {
                r.verdict = "Hi-Res (Analog/Vinyl Source)";
            }
        }
        else if (r.sampleRate <= 44100) {
            r.verdict = "CD Quality";
        }
        else {
            r.verdict = "High Fidelity";
        }

        // 4. Quality Notifications
        if (r.peak >= -0.01) {
            r.verdict += " (Clipped)";
        }

        // Deep Bit-Depth Analysis
        /*if (r.bitDepth >= 24) {
            if (r.noiseFloor > -70.0) {
                r.verdict += " (Analog Noise)"; // Noisier than 12-bit
            } else if (r.noiseFloor > -91.0) {
                r.verdict += " (16-bit Master)"; // Claims 24, but floor is at 16
            }
        }*/
    }

    private static void classify3(AudioAnalysisResult r) {
        // 1. Core Ratios
        double highToLowRatio = r.highBandRms - r.lowBandRms; // How much energy dropped?
        double snrHighBand = r.highBandRms - r.noiseFloor;  // Is there 'music' or just 'noise' at the top?

        // 2. The Verdict Chain
        // Lossy check: If energy drop is huge (>45dB) AND the signal is barely above noise floor (<10dB)
        boolean looksLossy = (highToLowRatio < -45.0) && (snrHighBand < 12.0);

        // Backup Lossy check: Extreme energy drop
        if (looksLossy || highToLowRatio < -100.0) {
            r.verdict = "Lossy Transcode (AAC/MP3)";
        }
        else if (r.sampleRate > 48000 && highToLowRatio < -65.0) {
            r.verdict = "Upsampled Hi-Res";
        }
        else if (r.sampleRate <= 44100) {
            r.verdict = "CD Quality";
        }
        else if (highToLowRatio > -40.0) {
            // Only call it Genuine if the high frequency isn't JUST noise
            if (snrHighBand > 15.0) {
                r.verdict = "Genuine Hi-Res";
            } else {
                r.verdict = "Hi-Res (Analog Source/Vinyl)";
            }
        }
        else {
            r.verdict = "High Fidelity";
        }

        // 3. Technical Warnings
        if (r.peak >= -0.01) {
            r.verdict += " (Clipped)";
        }

        // Deep Bit-Depth Analysis
        if (r.bitDepth >= 24) {
            if (r.noiseFloor > -70.0) {
                r.verdict += " (Analog Noise)"; // Noisier than 12-bit
            } else if (r.noiseFloor > -91.0) {
                r.verdict += " (16-bit Master)"; // Claims 24, but floor is at 16
            }
        }
    }

    private static void classify2(AudioAnalysisResult r) {
        // Difference between energy above 18kHz and below 18kHz
        double ultrasonicRatio = r.highBandRms - r.lowBandRms;

        // 2. Define Lossy Indicators (The MP3 Fingerprint)
        // - MP3s often have a very high noise floor (due to compression artifacts)
        // - MP3s often 'overshoot' 0dB resulting in positive peak values
        boolean hasHighNoise = r.noiseFloor > -50;
        boolean isClipped = r.peak >= -0.01;
        //boolean hasNoUltrasonics = ultrasonicRatio < -100;

        // 3. The Logic Chain
        if (!Double.isNaN(r.spectralFlatness) && r.spectralFlatness > 0.15) {
            r.verdict = "Lossy Transcode (AAC/MP3)";
        }
        // backup check for MP3 if flatness is NaN:
        // High Noise + Clipping + 44.1k sample rate is a classic 128-256kbps MP3 signature
        else if (r.sampleRate <= 48000 && hasHighNoise && isClipped) {
            r.verdict = "Lossy (Probable MP3/AAC)";
        }
        else if (r.sampleRate > 48000 && ultrasonicRatio < -65) {
            r.verdict = "Upsampled Hi-Res";
        }
        else if (r.sampleRate <= 44100) {
            // If it's 44.1kHz and silent above 18kHz, it's a standard CD.
            r.verdict = "CD Quality";
        }
        else if (ultrasonicRatio > -40) {
            r.verdict = "Genuine Hi-Res";
        }
        else {
            r.verdict = "High Fidelity";
        }

        // 2. The "Fake 24-bit" check (For your S25)
        // If a file claims to be 24-bit but has a 16-bit noise floor (-96dB)
        /*if (r.bitDepth >= 24 && r.noiseFloor > -90) {
            r.verdict += " (Padded 16-bit)";
        }*/

        // 3. Clipping check
        if (r.peak >= -0.01) {
            r.verdict += " (Clipped)";
        }
    }

    private static void classifyBak(AudioAnalysisResult r) {

      //  double ultrasonicRatio = r.highBandRms - r.lowBandRms;

        //if (r.bitDepth == 24 && r.noiseFloor > -96) {
        /*
        if (r.bitDepth >= 24 && r.noiseFloor > -92) {
            r.verdict = "Fake 24-bit (likely 16-bit master)";

        }
        else if (r.spectralFlatness > 0.28 && r.spectralEntropy > 0.92) {

            r.verdict = "Lossy Transcode (MP3/AAC)";

        }
        else if (ultrasonicRatio < -65 && r.sampleRate > 48000) {

            r.verdict = "Upsampled Hi-Res";

        }
        else if (r.sampleRate <= 44100) {

            r.verdict = "CD Quality";

        }
        else if (ultrasonicRatio > -45) {

            r.verdict = "Genuine Hi-Res";

        }
        else {

            r.verdict = "High Fidelity";
        } */

        double ultrasonicRatio = r.highBandRms - r.lowBandRms;
        // If the 'high energy' is just noise, don't call it Genuine.
        // Real music has a gap between the signal and the noise floor.
        /*boolean isJustNoise = r.highBandRms < (r.noiseFloor + 5);

        if (r.spectralFlatness > 0.28 && r.spectralEntropy > 0.92) {

            r.verdict = "Lossy Transcode (MP3/AAC)";

        }
        else if (ultrasonicRatio < -65 && r.sampleRate > 48000) {

            r.verdict = "Upsampled Hi-Res";

        }
        else if (r.sampleRate <= 44100) {

            r.verdict = "CD Quality";

        }
        if (ultrasonicRatio > -45 && !isJustNoise) {
            r.verdict = "Genuine Hi-Res";
        } else if (ultrasonicRatio > -45) {
            r.verdict = "Upsampled / Vinyl Rip (High Noise)";
        }
        else {

            r.verdict = "High Fidelity";
        }

        //if (r.peak >= -0.1) {
        if (r.peak >= -0.01) {
            r.verdict += " (Clipped Master)";
        } */

        // 1. Lossy Detection (AAC/MP3)
        // If flatness is NaN, we use the Ultrasonic Ratio as a backup for lossy detection
        boolean isLossy = (r.spectralFlatness > 0.15) || (ultrasonicRatio < -100);

        if (isLossy) {
            r.verdict = "Lossy Transcode (AAC/MP3)";
        }
        // 2. Fake Hi-Res Detection
        else if (ultrasonicRatio < -65 && r.sampleRate > 48000) {
            r.verdict = "Upsampled Hi-Res";
        }
        // 3. CD Quality
        else if (r.sampleRate <= 44100 || ultrasonicRatio < -60) {
            r.verdict = "CD Quality";
        }
        // 4. Genuine Hi-Res
        else if (ultrasonicRatio > -45) {
            r.verdict = "Genuine Hi-Res";
        }
        else {
            r.verdict = "High Fidelity";
        }

        // 5. Clipped Check (With a bit more headroom for lossy peaks)
        double peakThreshold = isLossy ? 0.5 : -0.01;
        if (r.peak >= peakThreshold) {
            r.verdict += " (Clipped Master)";
        }

        Log.d(TAG,
                "Verdict=" + r.verdict +
                        " DR=" + r.dynamicRange +
                        " Noise=" + r.noiseFloor +
                        " Ultrasonic=" + ultrasonicRatio);
    }
}