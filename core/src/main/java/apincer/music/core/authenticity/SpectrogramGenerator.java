package apincer.music.core.authenticity;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.util.Locale;

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

        String outputPath = context.getCacheDir() + "/spectrogram.jpg";
        FileSystem.delete(outputPath);

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

        /*
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
        */

        String analyserName = "MusicMate Spectra";
        String fontPath = ApplicationUtils.getPathOnAndroidFiles(context, "/webui/noto_sans_thai.ttf");
        // Values retrieved from your MediaTrack or TagRepository
        String qualityInfo = String.format(Locale.ENGLISH,"%s | %d-bit | %s Hz",
                codec.toUpperCase(),
                bitDepth,
                sampleRate);

        //int visualMaxFreq = (sampleRate / 2) + 2000; //Math.min(sampleRate / 2, 24000);
        // Professional Audiophile Logic
        int visualMaxFreq;
        if (sampleRate <= 48000) {
            // Show the whole range plus a small buffer to see the "wall"
            visualMaxFreq = (sampleRate / 2) + 1000;
        } else if (sampleRate <= 96000) {
            // Show up to 48kHz (High-Res territory)
            visualMaxFreq = 48000 + 500;
        } else {
            // Even for 192kHz, showing above 48kHz usually just shows noise.
            // 48kHz is enough to prove it's a High-Res file.
            visualMaxFreq = 48000 + 500;
        }

        String command =
                "-y " +
                        "-hide_banner -loglevel error " +
                        "-i \"" + inputPath + "\" " +
                        "-ar 48000 " + // Resample to max 48k to keep FFT math fast
                        "-ac 1 "+  // merge to 1 channels for speed
                        "-filter_complex "+
                        "\"showspectrumpic=" +
                        //"s=1080x720:" +
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
                        qualityInfo +
                        "':x=(w-text_w)/2:y=h-16:fontcolor=white:fontsize=24, "+
                        "drawtext=fontfile='"+
                        fontPath+
                        "':text='"+
                        analyserName+
                        "':x=32:y=8:fontcolor=white:fontsize=32\" "+
                        "-frames:v 1 \"" + outputPath + "\"";

        //(w-text_w)/2 automatically centers the text regardless of image width.

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