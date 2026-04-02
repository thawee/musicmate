package apincer.music.core.codec;

import android.content.Context;
import android.util.Log;

import com.antonkarpenko.ffmpegkit.FFmpegKit;
import com.antonkarpenko.ffmpegkit.FFmpegSession;
import com.antonkarpenko.ffmpegkit.ReturnCode;
import com.antonkarpenko.ffmpegkit.Session;

import java.io.File;
import java.util.Locale;

import apincer.music.core.model.Track;
import apincer.music.core.provider.FileSystem;
import apincer.music.core.utils.LogHelper;
import apincer.android.utils.FileUtils;

public class FFMpegHelper {

    private static final String TAG = "FFMpegHelper";

    public static void extractCoverArt(String path, File pathFile, GenerateCallback callback) {
        try {
            Log.d(TAG, "extractCoverArt: from:"+path+", to:"+pathFile);
            String targetPath = pathFile.getAbsolutePath();
            String options = " -c:v copy ";

            String cmd = " -hide_banner -nostats -i \"" + path + "\" " + options + " \"" + targetPath + "\"";
            LogHelper.setFFMpegOff();
            FFmpegKit.execute(cmd); // do not clear the result
            if(callback != null) {
                callback.onGenerated(targetPath, null,0,0);
            }
        }catch (Exception ex) {
            Log.e(TAG, "extractCoverArt", ex);
        }
    }

    public static void removeCoverArt(Context context, Track tag) {
            String pathFile = tag.getPath();
            String ext = FileUtils.getExtension(pathFile);
            pathFile = pathFile.replace("."+ext, "no_embed."+ext);
            String options = " -vn -codec:a copy ";
           // String options =" -map 0:V -y -codec copy ";

            String cmd = " -hide_banner -nostats -i \"" + tag.getPath() + "\" " + options + " \"" + pathFile+ "\"";
            LogHelper.setFFMpegOff();
            Session session = FFmpegKit.execute(cmd); // do not clear the result
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                FileSystem.safeMove(context, pathFile, tag.getPath(), true);
                //return pathFile;
            }else {
                FileSystem.delete(pathFile);
            }
    }

    public static final String KEY_BIT_RATE = "bit_rate";
    public static final String KEY_START_TIME = "start_time";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_TAG = "TAG:";
   public static final String KEY_TAG_ARTIST = "ARTIST";
    public static final String KEY_TAG_ALBUM = "ALBUM";
    public static final String KEY_TAG_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_COMPOSER = "COMPOSER";
    public static final String KEY_TAG_COMMENT = "COMMENT";
    public static final String KEY_TAG_COMPILATION = "COMPILATION";
    public static final String KEY_TAG_DISC = "disc"; //"DISCNUMBER";
    public static final String KEY_TAG_GENRE = "GENRE";
    public static final String KEY_TAG_GROUPING = "GROUPING";
    public static final String KEY_TAG_TRACK = "track";
    public static final String KEY_TAG_PUBLISHER = "PUBLISHER";

   // public static final String KEY_TAG_RATING = "RATING";
   public static final String KEY_TAG_TITLE = "TITLE";
    public static final String KEY_TAG_YEAR = "YEAR";

    // WAVE file
    //https://www.digitizationguidelines.gov/audio-visual/documents/listinfo.html
    public static final String KEY_TAG_WAVE_ARTIST = "IART"; //artist
    public static final String KEY_TAG_WAVE_ALBUM = "IPRD"; // album
    public static final String KEY_TAG_WAVE_ALBUM_ARTIST = "IENG"; // engineers
    public static final String KEY_TAG_WAVE_GENRE = "IGNR"; //genre
    public static final String KEY_TAG_WAVE_TRACK = "IPRT"; //track
    public static final String KEY_TAG_WAVE_TITLE = "INAM"; //title
    public static final String KEY_TAG_WAVE_YEAR = "date"; //""ICRD"; //date
    //public static final String KEY_TAG_WAVE_MEDIA = "IMED";
    public static final String KEY_TAG_WAVE_COMMENT = "ICMT"; // comment
    public static final String KEY_TAG_WAVE_PUBLISHER = "ISRC"; // name of person or organization
    public static final String KEY_TAG_WAVE_DISC = "ISRF"; // original form of material
    public static final String KEY_TAG_WAVE_GROUP = "IKEY"; // list of keyword, saperated by semicolon
    public static final String KEY_TAG_WAVE_COMPOSER = "ICMS"; // person who commision the subject
    public static final String KEY_TAG_WAVE_QUALITY = "ISBJ"; // Describes the contents of the file

    // AIF/AIFF
    public static final String KEY_TAG_AIF_ARTIST = "ARTIST";
    public static final String KEY_TAG_AIF_ALBUM = "ALBUM";
    public static final String KEY_TAG_AIF_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_AIF_COMPOSER = "COMPOSER";
    public static final String KEY_TAG_AIF_COMMENT = "COMMENT";
    public static final String KEY_TAG_AIF_COMPILATION = "COMPILATION";
    public static final String KEY_TAG_AIF_DISC = "disc"; //"DISCNUMBER";
    public static final String KEY_TAG_AIF_GENRE = "GENRE";
    public static final String KEY_TAG_AIF_GROUPING = "GROUPING";
    public static final String KEY_TAG_AIF_TRACK = "track";
    public static final String KEY_TAG_AIF_PUBLISHER = "PUBLISHER";
   public static final String KEY_TAG_AIF_QUALITY = "QUALITY";
    public static final String KEY_TAG_AIF_TITLE = "TITLE";
    public static final String KEY_TAG_AIF_YEAR = "YEAR";

    // QuickTime/MOV/MP4/M4A
    // https://wiki.multimedia.cx/index.php/FFmpeg_Metadata
    public static final String KEY_TAG_MP4_ARTIST = "artist"; //for aac
    public static final String KEY_TAG_MP4_AUTHOR = "author"; // for alac
    public static final String KEY_TAG_MP4_ALBUM = "album"; // album
    public static final String KEY_TAG_MP4_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_MP4_GENRE = "genre"; //genre
    public static final String KEY_TAG_MP4_TRACK = "track"; //track
    public static final String KEY_TAG_MP4_TITLE = "title"; //title
    public static final String KEY_TAG_MP4_YEAR = "year"; //date
    public static final String KEY_TAG_MP4_COMPOSER = "composer";
    public static final String KEY_TAG_MP4_GROUPING = "grouping";
    public static final String KEY_TAG_MP4_COMMENT = "comment";  // comment
    public static final String KEY_TAG_MP4_PUBLISHER = "copyright"; //copy right

    //https://gist.github.com/eyecatchup/0757b3d8b989fe433979db2ea7d95a01
    public static final String KEY_TAG_MP3_ARTIST = "artist"; //artist
    public static final String KEY_TAG_MP3_ALBUM = "album"; // album
    public static final String KEY_TAG_MP3_ALBUM_ARTIST = "album_artist";
    public static final String KEY_TAG_MP3_GENRE = "genre"; //genre
    public static final String KEY_TAG_MP3_TRACK = "track"; //track
    public static final String KEY_TAG_MP3_TITLE = "title"; //title
    public static final String KEY_TAG_MP3_YEAR = "date"; //date
    public static final String KEY_TAG_MP3_DISC = "disc";
    public static final String KEY_TAG_MP3_COMMENT = "comment";  // comment
    public static final String METADATA_KEY = "-metadata";

    /**
     * Converts an audio file to a different format using FFmpeg.
     *
     * <p>This method handles the conversion of an audio file from {@code srcPath} to
     * {@code targetPath} using the specified audio parameters. It supports special
     * filtering for DSF input and applies specific bit depths and compression levels
     * based on the target format.
     *
     * <p>The conversion is performed in a temporary directory. Only upon successful
     * completion will the temporary file be moved to the final {@code targetPath}.
     *
     * <p><b>Format-Specific Logic:</b>
     * <ul>
     * <li><b>DSF Input:</b> A 24kHz lowpass filter and 6dB volume gain are applied.
     * <li><b>FLAC Output:</b> Uses {@code cLevel} for compression (defaulting to 5)
     * and {@code bitDept} for the sample format (s16, s24, s32).</li>
     * <li><b>M4A (ALAC) Output:</b> Uses {@code bitDept} for the sample format
     * (s16, s24, s32).</li>
     * <li><b>AIFF (PCM) Output:</b> Uses {@code bitDept} to select the specific
     * PCM codec (pcm_s16be, pcm_s24be, pcm_s32be).</li>
     * <li><b>MP3 Output:</b> Ignores {@code bitDept} and encodes to 320k bitrate.</li>
     * </ul>
     *
     * @param context    The Android {@link Context} used for file system operations.
     * @param srcPath    The absolute file path of the source audio file to convert.
     * @param targetPath The absolute file path where the converted file should be saved.
     * If this file already exists, a suffix ("_001") will be appended.
     * @param cLevel     The desired compression level. Primarily used for FLAC (0-12).
     * An invalid value will result in a default (e.g., 5 for FLAC).
     * @param bitDept    The desired output bit depth (16, 24, or 32). This is only
     * applied to formats that support it (FLAC, ALAC, AIFF).
     * @return {@code true} if the conversion was successful and the file was moved
     * to {@code targetPath}, {@code false} otherwise (e.g., FFmpeg failure,
     * cancellation, or file I/O error).
     */
    public static boolean convert(Context context, String srcPath, String targetPath, int cLevel, int bitDept) {
        String options = "";

        if(bitDept ==1) {
            bitDept = 24; // dsd
        }

        // 1. Handle DSF input filters
        // We only apply the filter and resampler here.
        // The bit depth (-sample_fmt) will be set later based on the `bitDept` parameter.
        if (srcPath.toLowerCase().endsWith(".dsf")) {
            // convert from dsf
            // use lowpass filter to eliminate distortion and resample.
            options += " -af \"lowpass=24000, volume=6dB\" -ar 48000 ";
        }

        // 2. Determine the output sample format string based on bitDept
        // This will be used for codecs that respect -sample_fmt (like FLAC and ALAC).
        String sampleFmt = "";
        if (bitDept == 16) {
            sampleFmt = " -sample_fmt s16 ";
        } else if (bitDept == 24) {
            sampleFmt = " -sample_fmt s24 ";
        } else if (bitDept == 32) {
            sampleFmt = " -sample_fmt s32 ";
        }
        // If bitDept is 0 or another value, we don't pass the flag,
        // letting FFmpeg choose a suitable default.

        // 3. Set codec options based on target file extension
        String targetExt = FileUtils.getExtension(targetPath.toLowerCase(Locale.US));

        if (targetExt.endsWith("flac")) {
            // FLAC respects -sample_fmt for bit depth.
            // Use 5 as a default compression if cLevel is invalid.
            int compression = (cLevel >= 0 && cLevel <= 12) ? cLevel : 5;
            options += sampleFmt + " -y -vn -c:a flac -compression_level " + compression;

        } else if (targetExt.endsWith("mp3")) {
            // MP3 is lossy and doesn't have a PCM bit depth.
            // We ignore `bitDept` and `sampleFmt`.
            options += " -y -vn -c:a libmp3lame -b:a 320k ";

        } else if (targetExt.endsWith("m4a")) {
            // Assuming ALAC (Apple Lossless), which respects -sample_fmt.
            // Your old code `pcm_s...be` was for raw PCM, not ALAC. This is correct.
            options += sampleFmt + " -y -vn -c:a alac ";

        } else if (targetExt.endsWith("aiff")) {
            // For uncompressed PCM like AIFF, we set the bit depth
            // by choosing the specific codec name. We ignore `sampleFmt`.
            if (bitDept == 24) {
                options += " -y -vn -c:a pcm_s24be "; // 24-bit Big Endian
            } else if (bitDept == 32) {
                options += " -y -vn -c:a pcm_s32be "; // 32-bit Big Endian
            } else {
                // Default to 16-bit for AIFF
                options += " -y -vn -c:a pcm_s16be "; // 16-bit Big Endian
            }
        } else {
            Log.e(TAG, "Unsupported target format: " + targetPath);
            return false;
        }

        Log.i(TAG, "Converting: " + srcPath);

        String ext = FileUtils.getExtension(srcPath);

        String tmpTarget = srcPath.replace("." + ext, "_NEWFMT." + targetExt);

        String cmd = " -hide_banner -nostats -i \"" + srcPath + "\" " + options + " \"" + tmpTarget + "\"";
        Log.i(TAG, "Converting with cmd: " + cmd);

        try {
            LogHelper.setFFMpegOff();
            FFmpegSession session = FFmpegKit.execute(cmd);

            // *** IMPORTANT FIX ***
            // You must check for SUCCESS, not just "not cancel".
            // A failed (but not cancelled) session would have been treated as a success.
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                Log.i(TAG, "Conversion successful: " + srcPath);
                File targetFile = new File(targetPath);
                if (targetFile.exists()) {
                    // Consider a better way to handle existing files
                    // This logic is risky, e.g., "song.v1.mp3" -> "song_001.v1.mp3"
                    targetPath = targetPath.replaceFirst("\\.(?=[^\\.]+$)", "_001.");
                }

                return FileSystem.move(context, tmpTarget, targetPath);
            } else {
                // Conversion failed or was cancelled
                Log.e(TAG, String.format("Conversion failed. RC: %s. Logs:\n%s",
                        session.getReturnCode(), session.getAllLogsAsString()));
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "FFmpeg execution threw an exception", e);
            return false;
        } finally {
            // Always clean up the temp *source* file
           // FileSystem.delete(tmpPath);
            // Also clean up the temp *target* file in case of failure
            FileSystem.delete(tmpTarget);
        }
    }

    // Update your interface to handle the new data
    public interface GenerateCallback {
        void onGenerated(String path, String qualityStatus, int realBits, double highFreqDb);
        void onError(String error);
    }

}
