package apincer.music.core.authenticity;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.ReturnCode;

import java.util.Locale;

import apincer.android.utils.FileUtils;
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

        String filename = FileUtils.getFileName(inputPath);
        int visualMaxFreq = sampleRate / 2; //Math.min(sampleRate / 2, 24000);
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

        String fontPath = ApplicationUtils.getPathOnAndroidFiles(context, "/webui/noto_sans_thai.ttf");
        // Values retrieved from your MediaTrack or TagRepository
        String qualityInfo = String.format(Locale.ENGLISH,"%s | %d-bit | %s Hz",
                codec.toUpperCase(),
                bitDepth,
                sampleRate);

        String command =
                "-y " +
                        "-hide_banner -loglevel error " +
                        //"-ss 30 -t 20 " +
                        "-i \"" + inputPath + "\" " +
                        "-ac 1 "+
                        "-filter_complex "+
                        "\"showspectrumpic=" +
                        "s=1080x720:" +
                        "legend=1:" +
                        "scale=log:" +
                        "fscale=lin:" +
                        "stop="+visualMaxFreq+":"+
                        "color=magma:" +
                        "drange=120:" +
                        "win_func=hanning[v]; "+
                        "[v]drawtext=fontfile='"+
                        fontPath+
                        "':text='MusicMate Analysis':x=48:y=16:fontcolor=white:fontsize=32, "
                        +"drawtext=fontfile='"+
                        fontPath+
                        "':text='"
                        +qualityInfo
                        +"':x=420:y=20:fontcolor=white:fontsize=24\" "
                        +"-frames:v 1 \"" + outputPath + "\"";

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