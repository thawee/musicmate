package apincer.music.core.authenticity;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.io.File;

import apincer.music.core.provider.FileSystem;
import apincer.music.core.utils.ApplicationUtils;

public class SpectrogramGenerator {

    private static final String TAG = "SpectrogramGenerator";

    public interface Callback {
        void onSuccess(String outputPath);
        void onError(String error);
    }

    /**
     * Generate a high-quality spectrogram image for audio analysis.
     *
     * This spectrogram configuration is optimized for detecting:
     * - MP3/AAC compression cutoffs
     * - Upsampled Hi-Res audio
     * - Ultrasonic content
     * - Clipping artifacts
     * - Noise shaping
     *
     * The analyzer processes only a short segment of the track
     * (20 seconds starting at 30 seconds) for performance.
     *
     * @param context Android context
     * @param inputPath audio file path
     * @param callback result callback
     */
    public static void generate(Context context, String inputPath, String codec, int bitDepth, int sampleRate, Callback callback) {

        // Clean up old spectrogram cache files to prevent storage bloat
        try {
            File cacheDir = context.getCacheDir();
            File[] oldSpectrograms = cacheDir.listFiles((dir, name) -> name.startsWith("spectrogram_") && name.endsWith(".jpg"));
            if (oldSpectrograms != null) {
                for (File f : oldSpectrograms) {
                    f.delete();
                }
            }
        } catch (Exception ignored) {}

        String hash = Integer.toHexString((inputPath + "_" + System.currentTimeMillis()).hashCode());
        String outputPath = context.getCacheDir() + "/spectrogram_" + hash + ".jpg";

        String analyserName = "MusicMate Spectra";
        String fontPath = ApplicationUtils.getPathOnAndroidFiles(context, "/webui/noto_sans_thai.ttf");

        // Professional Audiophile Logic
        int visualMaxFreq;
        if (sampleRate <= 48000) {
            // Show the whole range plus a small buffer to see the "wall"
            visualMaxFreq = (sampleRate / 2) + 1000;
        } else {
            // High-Res territory: show up to 48kHz
            visualMaxFreq = 48000 + 500;
        }

        // For ultra high-res (>96kHz such as 192k/384k), resample to 96k to keep FFT math fast while preserving full 48kHz ultrasonic spectrum
        String resampleArg = (sampleRate > 96000) ? "-ar 96000 " : "";

        String command =
                "-y " +
                        "-hide_banner -loglevel error " +
                        "-i \"" + inputPath + "\" " +
                        resampleArg +
                        "-ac 1 "+  // merge to 1 channel for speed
                        "-filter_complex "+
                        "\"showspectrumpic=" +
                        "s=1080x1024:" +
                        "legend=1:" +
                        "scale=log:" + // Logarithmic intensity for better colors
                        "fscale=lin:" + // Linear frequency for technical cutoff checks
                        "stop="+visualMaxFreq+":"+  // maximum hz axis
                        "color=magma:" +
                        "drange=120:" +
                        "win_func=hanning[v]; "+ // for analys audio file
                        "[v]drawtext=fontfile='"+
                        fontPath+
                        "':text='"+
                        analyserName+
                        "':x=32:y=8:fontcolor=gray:fontsize=32\" "+
                        "-frames:v 1 \"" + outputPath + "\"";

        Log.d(TAG, "Running FFmpeg: " + command);

        FFmpegKit.executeAsync(command, session -> {
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                Log.d(TAG, "Spectrogram created: " + outputPath);
                callback.onSuccess(outputPath);
            } else {
                Log.e(TAG, "FFmpeg failed: " + session.getFailStackTrace());
                callback.onError("Spectrogram generation failed");
            }
        });
    }
}