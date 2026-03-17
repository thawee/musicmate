package apincer.music.core.authenticity;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import apincer.music.core.provider.FileSystem;

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
    public static void generate(Context context, String inputPath, int sampleRate, Callback callback) {

        String outputPath = context.getCacheDir() + "/spectrogram.jpg";
        FileSystem.delete(outputPath);

        int visualMaxFreq = Math.min(sampleRate / 2, 24000);
        /*
         Mastering-grade spectrogram parameters:

         s=1000x700      -> high resolution
         scale=log       -> logarithmic amplitude
         fscale=log      -> logarithmic frequency scale
         color=viridis   -> readable color scheme
         legend=1        -> frequency scale labels
         drange=120      -> wide dynamic range

         -ss 30 -t 20    -> analyze middle of track for better accuracy
         */

        String command =
                "-y " +
                        "-hide_banner -loglevel error " +
                        "-ss 30 -t 20 " +
                        "-i \"" + inputPath + "\" " +
                        "-lavfi \"showspectrumpic=" +
                        "s=1000x700:" +
                        "legend=1:" +
                        "scale=log:" +
                        "fscale=log:" +
                        "stop="+visualMaxFreq+":"+
                        "color=viridis:" +
                        "drange=120\" " +
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